# Twelve-Factor Audit — 2026-07-10

Re-audit of the notification service against the Twelve-Factor App methodology, comparing
against the prior audit's findings for factors V, VI, VIII, IX, and X.

## Before / After

| # | Factor | Before | After | Evidence |
|---|---|---|---|---|
| I | Codebase | Compliant | Compliant | Single repo, single remote, no forked siblings. |
| II | Dependencies | Compliant | Compliant | `pom.xml` pins Quarkus BOM `3.28.4`; no version ranges; `mvnw`/`mvnw.cmd` committed. |
| III | Config | Compliant | Compliant | `application.properties` reads secrets/hosts via `${ENV_VAR}`; `.env` untracked, `.env.example` present. |
| IV | Backing services | Compliant | Compliant | `EmailSenderFactory`/`SmsSenderFactory` resolve provider by config key, not code branch; Redis/Postgres reached only via env vars. |
| V | Build, release, run | Schema owned by Hibernate auto-DDL; no distinct release stage | **Fixed** | `application.properties` sets `schema-management.strategy=validate` (no auto-DDL) with Flyway owning migrations; `.github/workflows/build.yml` builds one immutable image per sha, gated on tests, before release. |
| VI | Backing services (queue) | In-memory queue, not swappable | **Fixed** | In-memory SmallRye channel replaced by Postgres-backed durable queue; `NotificationRepository.claimBatch()` uses `FOR UPDATE SKIP LOCKED`. |
| VII | Port binding | Compliant | Compliant | `quarkus.http.port=${APP_INTERNAL_PORT:8080}`; service self-contained via embedded Vert.x HTTP. |
| VIII | Concurrency | Scheduler unsafe on multiple replicas | **Fixed (queue path); partial gap remains** | `QueuePoller` claims work safely across replicas via `SELECT ... FOR UPDATE SKIP LOCKED`. Residual: `RetryScheduler`'s `@Scheduled` still runs on every replica with no leader election — currently harmless only because the downstream claim is idempotent, not because the race is closed. |
| IX | Disposability | Shutdown didn't drain in-flight work | **Fixed** | `QueuePoller`'s `@PreDestroy shutdown()` stops claiming and blocks (up to `quarkus.shutdown.timeout`) draining in-flight work; k8s `terminationGracePeriodSeconds: 45` exceeds the app's shutdown budget. |
| X | Dev/prod parity | JVM (dev) vs native (prod) untested | **Fixed, with one open gap** | CI runs a `native-build-test` job (`mvn verify -Pnative`) on every master push. Not yet closed: that job doesn't gate the `release` job, so a native-only regression could still ship before being caught. |
| XI | Logs | Compliant | Compliant | No file appenders; logs go to console only (`quarkus.log.console.format`). |
| XII | Admin processes | Not previously audited | Partial | Flyway migrations run automatically at boot in the same image. Gap: ops scripts (`scripts/*.ps1`) are Windows-only, no portable/containerized equivalents. |

## Remaining gaps

1. **VIII** — `RetryScheduler`/`RedisRetryService.fetchDueNotifications()` reads and removes due
   items without a lock or leader election; safe today only because the downstream write is
   idempotent. Add a guard (leader lock or atomic Lua read+remove) so this isn't relying on
   incidental idempotency.
2. **X** — `native-build-test` doesn't gate `release` in `.github/workflows/build.yml`; either wire
   it as a required check or document that native breakage is an accepted risk.
3. **XII** — Operational scripts are PowerShell-only; on-call response is limited to Windows
   operators. Consider containerizing or porting critical scripts (e.g. `clear-redis.ps1`).

## Scope note

Factors I–IV, VII, XI were re-verified but were already compliant in the prior audit and show no
regressions. Factor XII wasn't scored in the original audit; it's included here for completeness
and is rated partial.
