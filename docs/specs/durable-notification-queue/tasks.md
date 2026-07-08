# Tasks: durable-notification-queue

Execution discipline: one task per commit, tests in the same commit as the task
that needs them. If a task turns out bigger than scoped here mid-flight, stop and
report back rather than improvising a workaround.

- [x] T1: Add Flyway migration for queue-claim columns — pre-check: confirmed
      neither `V1.0.1__Fix_Notification_Attempts_Id_Type.sql` nor
      `V1.1.0__Add_test_column.sql` exists in the repo or anywhere in git history
      (checked `git log --all --full-history`); dev DB's `flyway_schema_history` is
      unverified (Docker wasn't running) — user to check/clean manually. Files:
      `src/main/resources/db/migrations/V1.0.1__Add_queue_claim_columns.sql` — adds
      `locked_by VARCHAR` (nullable), `locked_at TIMESTAMP` (nullable),
      `attempts_count INT NOT NULL DEFAULT 0` to `notifications`, plus a composite
      index `(status, created_at)` to support the claim query's filter+order; top-of-
      file comment states data impact (none — all nullable/defaulted, safe on a
      non-empty table). Done when: migration applies cleanly via
      `%test.quarkus.flyway.clean-at-start` and all existing tests still pass
      unmodified.

- [x] T2: Add claim-and-reap query to `NotificationRepository` — files:
      `NotificationRepository.java`, new `NotificationRepositoryTest` case(s) —
      `claimBatch(int limit, String workerId)` runs one native `UPDATE ...
      SET status='PROCESSING', locked_by=?, locked_at=now() WHERE id IN (SELECT id
      FROM notifications WHERE status='QUEUED' OR (status='PROCESSING' AND
      locked_at < now() - VISIBILITY_TIMEOUT) ORDER BY created_at FOR UPDATE SKIP
      LOCKED LIMIT ?) RETURNING *` (or Panache-native equivalent), returning claimed
      rows tagged with whether each was fresh (`QUEUED`) or reaped (`PROCESSING`).
      Done when: a test with two concurrent callers against the same claimable set
      proves no row is claimed twice, and a row with `locked_at` older than the
      timeout is reclaimed.

- [x] T3: Wire queue config — files: `application.properties`, `.env.example`, new
      `QueueConfig` (mirroring the existing `RedisConfig`/`ApplicationConfig`
      `@ConfigMapping` pattern) — add `POLL_INTERVAL` (default `500ms`),
      `POLL_BATCH_SIZE` (default reasonable value, e.g. `20`), `VISIBILITY_TIMEOUT`
      (default `60s`), `MAX_COLD_RETRY_CYCLES` (default e.g. `5`), and set
      `quarkus.shutdown.timeout` (propose `30s`, confirm no existing value relied on
      the default). Also wire the already-present-but-unused `WORKER_CONCURRENCY`
      (`.env.example:65`) into `QueueConfig`. Done when: app boots in dev and test
      profiles with defaults, `QueueConfig` values are injectable and covered by a
      config-mapping test.

- [x] T4: Replace channel dispatch with poller-driven claim. Done as scoped, plus
      two corrections discovered mid-flight (both recorded in plan.md, user
      approved fixes before continuing): (1) `NotificationRecord` didn't persist
      `locale`/raw `message`, which would have silently lost data on every poller
      claim, not just crash recovery — closed with migration V1.0.2 and
      entity/service wiring; (2) Quarkus's built-in `@Scheduled` doesn't support
      sub-1s intervals — `POLL_INTERVAL` default corrected from `500ms` to `1s`
      (plan.md decision #1 updated accordingly). Also converted
      `NotificationService`/`NotificationProcessor`/`RetryScheduler` from field to
      constructor injection (required by the repo's injection-style guard once
      these files were touched) and fixed a latent Awaitility/QuarkusTransaction
      race in `testSendEmailFailureAndRetry` that the real poller tick exposed.

- [x] T5: Cap poison-message retries — files: `RetryScheduler.java`,
      `NotificationStateService.java` (or wherever `attempts_count` is incremented
      per full Layer-1+cold-queue cycle) — after `MAX_COLD_RETRY_CYCLES`, stop
      resurrecting from the Redis cold queue and leave the row as terminal `FAILED`
      (no new enum value, per decision #3). Cold-queue resurrection is explicit:
      `RetryScheduler` flips the row's status back to `QUEUED` and removes it from
      the Redis ZSET (`RedisRetryService.fetchDueNotifications()` already pops it);
      the poller then claims it through the normal `claimBatch()` path — no direct
      hand-off. Code comment at the flip site notes why concurrent resurrection by
      multiple replicas is safe: the status flip to `QUEUED` is idempotent (every
      replica's `RetryScheduler` does the same flip) and the actual work is
      serialized by `claimBatch()`'s `FOR UPDATE SKIP LOCKED`, so only one replica
      ever wins the claim regardless of how many flipped it. Done when: a test
      drives a permanently-failing notification through `MAX_COLD_RETRY_CYCLES` and
      asserts `RetryScheduler` stops re-enqueuing it afterward (closes the existing
      infinite-retry gap).

- [x] T6: Add send-time dedup guard — files: `DeduplicationService.java` (add an
      ID-keyed check, reusing the existing Redis-backed mechanism, not a new one),
      `NotificationProcessor.java` (call it immediately before the channel-strategy
      `send()` to check, and mark the ID as sent **only after `send()` returns
      successfully** — never before, so a failed send remains retryable and isn't
      permanently blocked by its own dedup marker). Done when: two tests both pass —
      (1) a simulated reaper double-claim (two calls to `processNotification()` for
      the same notification ID) results in exactly one `send()` call; (2) a
      notification whose first `send()` throws and is retried (Layer-1 retry or
      cold-queue resurrection) still successfully sends on the later attempt.

- [x] T7: Real graceful drain on shutdown — files: `NotificationProcessor.java`
      (replace the `volatile boolean` flag with a shutdown path that stops the
      poller claiming new batches immediately, then blocks until the in-flight
      counter reaches zero, bounded by `quarkus.shutdown.timeout`), `QueuePoller.java`
      (expose a way to halt claiming). Done when: a **dedicated** test starts
      in-flight work, triggers shutdown, and asserts it blocks until drained and
      completes within the configured timeout (per constraint #6 — not just that a
      flag flips).

- [x] T8: Queue observability — files: new `QueueMetricsService` (Micrometer, not
      the existing Redis-counter `MetricsService` pattern) registering
      `notifications.queue.depth` (gauge), `notifications.queue.oldest.age.seconds`
      (gauge), `notifications.queue.claimed.total`,
      `notifications.queue.reaped.total`, `notifications.queue.poisoned.total`
      (counters); wired into `claimBatch()`/`QueuePoller`/T5's poison path. Done
      when: a test asserts each counter increments on its triggering event and both
      gauges reflect actual DB state via the Micrometer test registry.

- [x] T9: Drop unused messaging dependency (conditional) — files: `pom.xml` — grep
      confirms zero remaining `@Incoming`/`@Outgoing`/`Emitter`/`mp.messaging` usage
      after T4; if none, remove `quarkus-messaging` from `pom.xml`. Done when: build
      and full test suite pass without the dependency (or, if something still needs
      it, this task is marked deferred with the reason).

- [ ] T10: End-to-end crash-recovery integration test — files: new
      `QueueDurabilityTest` (extends `BaseIntegrationTest`) — simulate a
      crash-mid-processing (persist a `QUEUED` row, manually flip to `PROCESSING`
      with a stale `locked_at` beyond `VISIBILITY_TIMEOUT`) and assert the poller
      reclaims and successfully delivers it exactly once; separately, assert two
      `QueuePoller` instances (simulating two replicas) never both claim the same
      row under concurrent load. Done when: both scenarios pass against the existing
      Testcontainers Postgres/Redis setup with zero new containers.
