# Durable notification queue

## Problem

`NotificationService.dispatchNotification()` persists a `NotificationRecord` (status
`QUEUED`) and then calls `notificationEmitter.send(request)` on the
`@Channel("notification-queue")` emitter (`NotificationService.java:44-45,108-111`). No
`mp.messaging.*` connector is configured anywhere in `application.properties`, so
SmallRye silently falls back to its in-memory connector. Consequences, confirmed by
reading the code:

- Any message between `emitter.send()` and the `@Incoming("notification-queue")`
  handler finishing (`NotificationProcessor.java:37-82`) is held only in JVM heap. A
  crash, OOM-kill, or rolling deploy drops it — the persisted `QUEUED` row is never
  retried by anything (`RetryScheduler` only resurrects rows that already reached the
  Redis cold queue via `fallbackToRedis`, `RetryScheduler.java:26-36`).
- Running more than one replica means each replica has its own in-memory channel, so
  there is no shared work distribution — this is fine today only because the service
  runs as a single instance.
- `NotificationProcessor.shutdown()` (`NotificationProcessor.java:111-114`) sets a
  `volatile boolean` that new invocations check, but does nothing to wait for
  in-flight `processNotification()` calls (each on its own virtual thread via
  `@RunOnVirtualThread`) to finish. `quarkus.shutdown.timeout` is not set anywhere, so
  Quarkus uses its default grace period with no guarantee in-flight sends actually
  complete before the process is killed.
- Separately, and already present today regardless of this fix: `RetryScheduler`
  re-enqueues everything it pulls from the Redis cold queue forever — there is no
  attempt cap, so a permanently-failing recipient cycles through Layer‑1 retry →
  cold queue → Layer‑1 retry indefinitely (`RetryScheduler.java:31-35`,
  `NotificationProcessor.java:88-109`).

## Goal / Non-goals

Goals:
- A notification, once `dispatchNotification()` returns 202, survives a crash/restart
  of the processing side without being lost.
- The service can run N replicas with each `QUEUED` notification processed by exactly
  one replica at a time (no duplicate work, no starved rows).
- `@PreDestroy` (or its replacement) actually drains in-flight work, bounded by
  `quarkus.shutdown.timeout`, before the process exits.
- Duplicate delivery, poison messages, and startup-time unavailability of whatever
  backs the queue are explicitly handled, not accidental.
- All new tunables are env-var driven and added to `.env.example`; docker-compose and
  the `%test` profile keep working with zero new containers.

Non-goals (explicitly out of scope for this mission):
- Fixing the `NotificationAttempt` id type (UUID vs `Long`) — separate mission.
- Any k8s manifest or CI pipeline change — separate mission.
- Rewriting the channel-send strategies (email/SMS/Telegram) themselves.
- Rewriting `DeduplicationService`'s intake-time hashing logic — we only add a second,
  narrower guard (see Failure modes), we don't touch the existing one.
- Introducing a brand-new backing service (see Design — this is a deliberate
  rejection, not an oversight).

## Design

**Chosen approach: Postgres-backed durable queue (transactional outbox), claimed via
`SELECT ... FOR UPDATE SKIP LOCKED`, driven by a new scheduled poller — replacing the
in-memory SmallRye channel entirely.**

The `notifications` table is already, in effect, an outbox: `persistRecord()` commits
a `QUEUED` row in its own transaction (`REQUIRES_NEW`, `NotificationService.java:91`)
*before* the in-memory `enqueue()` call. The durability gap is entirely in the second
half. Closing it means: stop treating the emitter as the queue, and make the `QUEUED`
row in Postgres the actual queue, claimed with `FOR UPDATE SKIP LOCKED` so competing
replicas never claim the same row.

Alternatives considered and rejected:

- **(a) Redis Streams.** Rejected as primary mechanism. Confirmed via Quarkus's own
  messaging guide: there is no first-party `smallrye`/Quarkus reactive-messaging Redis
  Streams connector (Kafka, Pulsar, RabbitMQ, AMQP, MQTT are the supported connectors;
  Redis isn't among them). `quarkus-redis-client` (already a dependency, `pom.xml:149`)
  does expose low-level `StreamCommands` (`XADD`/`XREADGROUP`/`XACK`) via
  `RedisDataSource.stream()`, so it's *possible*, but only by hand-rolling consumer
  groups, pending-entry-list handling, and `XCLAIM`/`XAUTOCLAIM` for stuck messages —
  materially more new code than reusing Postgres, for a store that this codebase
  already treats as ephemeral/cache-tier (dedup TTL keys, rate-limit counters, and the
  cold-retry ZSET), not as a durability source of truth.
- **(b) RabbitMQ connector (`smallrye-rabbitmq`).** Rejected. It's a fully first-party,
  config-driven connector with native competing-consumers and DLQ support — the
  cleanest option on paper — but it means standing up an entirely new backing service:
  new docker-compose container, new Testcontainers module, new credentials and
  operational surface (monitoring, backups, upgrades), when Postgres — already a hard
  dependency via Flyway — can deliver the same durability and multi-replica guarantee
  for free. Worth revisiting only if polling-based throughput ever becomes the
  bottleneck.
- **(c) Postgres transactional outbox + poller.** Chosen. Zero new backing services,
  zero new client libraries, reuses the existing `notifications` table and index
  (`idx_notification_status`), and is a direct, natural tightening of the
  persist-then-dispatch pattern that already exists.

New/changed components:
- `NotificationRepository`: add a `claimBatch(int limit, String workerId)` method
  (native query, `SELECT ... FOR UPDATE SKIP LOCKED`) that atomically selects
  `QUEUED` rows (and reaps expired `PROCESSING` rows — see below) and flips them to
  `PROCESSING` in the same transaction.
- New `QueuePoller` (`@Scheduled`, short interval, e.g. `POLL_INTERVAL=500ms`,
  configurable) replaces the `@Channel`/`Emitter` wiring as the thing that drives
  work into `NotificationProcessor`. It claims a batch, then dispatches each claimed
  row to `processNotification()` on a virtual thread, bounded by a concurrency limit
  — reusing `WORKER_CONCURRENCY`, which already exists in `.env.example:65` but is
  currently unused by any code.
- `NotificationProcessor`: drop `@Incoming("notification-queue")` and the channel
  indirection; `processNotification()` becomes a plain method the poller calls
  directly (its `@Retry`/`@Fallback` fault-tolerance interceptors keep working
  regardless of how the method is invoked — they're CDI interceptors, not
  messaging-specific). Add in-flight tracking (e.g. an `AtomicInteger`/id set)
  incremented before dispatch and decremented in `finally`, for graceful drain.
- Reaper (same poller tick): `PROCESSING` rows whose claim is older than a
  `VISIBILITY_TIMEOUT` are reclaimed back to `QUEUED` — an SQS-style visibility
  timeout, covering the crash-mid-processing case.
- Graceful shutdown: replace the `volatile boolean` flag with a shutdown path that (1)
  immediately stops the poller from claiming new batches, then (2) blocks, bounded by
  `quarkus.shutdown.timeout`, until the in-flight counter reaches zero.
- `NotificationService.dispatchNotification()` loses the `Emitter`/`enqueue()` call
  entirely — persisting the `QUEUED` row *is* the enqueue.
- `notifications` table: new Flyway migration adding `locked_by`, `locked_at`, and
  `attempts_count` columns, needed by the reaper and by poison-message handling.
- New env vars, all wired into `.env.example`: `POLL_INTERVAL` (default `500ms`),
  `POLL_BATCH_SIZE`, `VISIBILITY_TIMEOUT` (default `60s`), `MAX_COLD_RETRY_CYCLES`,
  plus setting `quarkus.shutdown.timeout` (currently unset anywhere).
- If, once `@Channel`/`Emitter` are removed, `quarkus-messaging` has no remaining
  usage in the codebase, removing it from `pom.xml` is its own task (see tasks.md) —
  not folded into the poller/processor task.

## Observability (per observability-conventions)

Micrometer (already a dependency — `quarkus-micrometer-registry-prometheus`,
`pom.xml:108` — note this is *not* what `MetricsService` currently uses; that class
does its own Redis-INCR counters. The new queue metrics use real Micrometer, matching
convention, not the existing ad hoc pattern):

- `notifications.queue.depth` — gauge, count of rows with `status=QUEUED`.
- `notifications.queue.oldest.age.seconds` — gauge, age of the oldest `QUEUED` row
  (0 when queue is empty).
- `notifications.queue.claimed.total` — counter, incremented per row claimed by
  `claimBatch()`.
- `notifications.queue.reaped.total` — counter, incremented per row the visibility
  timeout reclaims from `PROCESSING` back to `QUEUED`.
- `notifications.queue.poisoned.total` — counter, incremented per row moved to
  terminal `FAILED` after `MAX_COLD_RETRY_CYCLES`.

Dimensions as tags, not names — no unbounded values (recipient, notification id) as
tags anywhere.

## Constraints for execution

- `QueuePoller` must invoke `processNotification()` through the **injected CDI bean**
  reference (`NotificationProcessor`), never via self-invocation on the same bean —
  self-invocation bypasses the `@Retry`/`@Fallback` interceptors silently (no error,
  just no fault tolerance), which would quietly undo the existing resilience layers.
- Graceful-drain behavior gets its own dedicated test: start in-flight work, trigger
  shutdown, assert it blocks until drained and completes within
  `quarkus.shutdown.timeout` (not just that the flag flips).

## Failure modes

- **Crash mid-processing**: row stuck in `PROCESSING`, reclaimed by the reaper after
  `VISIBILITY_TIMEOUT` and reprocessed. `VISIBILITY_TIMEOUT` must safely exceed the
  worst-case full `@Retry`+`@Fallback` cycle time (see open questions).
- **Duplicate delivery**: the existing `DeduplicationService` guards at *intake*
  (`NotificationService.java:52-56`), keyed on recipient+channel+content hash. A
  reaper reclaim (row wasn't actually dead, just slow) can still cause a second
  *send* of the same row. Add a second, narrower dedup guard immediately before the
  actual channel-strategy `send()` call in `NotificationProcessor`, keyed on
  notification ID (not content hash) — deliberately using
  `DeduplicationService`'s existing Redis-backed mechanism rather than inventing a new
  one.
- **Poison messages**: increment `attempts_count` each full Layer‑1+cold-queue cycle.
  After `MAX_COLD_RETRY_CYCLES`, stop resurrecting — this closes the infinite-retry
  gap that exists in `RetryScheduler` today (see Problem) — and leave the row as a
  terminal failure, visible through the existing `GetFailedNotifications`
  endpoint/query (`NotificationService.java:70-76`).
- **Backing-store unavailable at startup**: Postgres is already a hard dependency
  (Flyway `migrate-at-start=true`) — this design adds no new startup-availability
  risk. Redis being down at startup already degrades the cold-queue path today and is
  unchanged by this work.

## Multi-replica behavior

`FOR UPDATE SKIP LOCKED` gives competing-consumers semantics across N replicas for
free — no consumer-group bookkeeping needed, unlike Redis Streams or RabbitMQ.

## Local dev / Testcontainers

No new containers. `TestResources.java` already starts Postgres + Redis
(`TestResources.java:12-18`); the poller and reaper are testable through the existing
`BaseIntegrationTest`/`QuarkusTest` setup. `docker-compose.yaml` needs no new service.

## Decisions (resolved 2026-07-08)

1. `POLL_INTERVAL` default `500ms`, env-tunable.
2. `VISIBILITY_TIMEOUT` default `60s`, env-tunable — comfortably exceeds the current
   worst-case `RETRY_MAX_ATTEMPTS` × `RETRY_DELAY` (3 × 2000ms) plus real send
   latency.
3. Poison-message terminal state reuses `FAILED` + `attempts_count` threshold — no
   new `NotificationStatus` enum value in this mission.
4. `@Channel`/`Emitter` are dropped entirely. If `quarkus-messaging` ends up unused,
   removing it from `pom.xml` is a separate task, not assumed.

## Risks & open questions

None outstanding — all four raised in the initial plan were resolved above.
