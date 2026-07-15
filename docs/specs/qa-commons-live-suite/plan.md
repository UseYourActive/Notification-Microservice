# qa-commons live suite

## Problem

This repo has exactly one black-box test: `ChannelsQaCommonsConsumptionTest`, a
proof that qa-commons-api v0.1.0 can be consumed from JitPack and drive this
service's `GET /api/v1/channels` from outside the process. It proves the
plumbing works but covers none of the service's actual behavior — sending a
notification, persistence, failure/validation paths, or the other read
endpoints are untested from a deployed-instance perspective. Everything else
that verifies this service is a `@QuarkusTest`/WireMock-based internal test
(163 tests) that never leaves the JVM. qa-commons has since reached v0.3.0
and gained a `db` module whose `QA_DB_*` config defaults were built to match
this exact service's own compose stack, making a real Postgres-row oracle
possible for the first time.

## Goal / Non-goals

**Goals**
- Bump `qa-commons-api` to v0.3.0 and add `qa-commons-db` (both `test` scope).
- Grow `@Tag("live")` coverage: send (positive + validation negative), send +
  DB oracle, duplicate-send independence, and contract-shape checks for
  channels, failed-list, and metrics-today.
- Re-verify every endpoint's actual shape against the running service's
  `/q/openapi` before locking a test — the v0.1.0 proof is nearly a year of
  drift old.
- Fingerprint-verify the target Postgres (via `flyway_schema_history`) once
  per suite run before any test trusts a row assertion.
- Document the known invalid-channel → 500 bug as a finding, without fixing
  or working around it.
- Add one README paragraph naming the two-tier test strategy.

**Non-goals**
- No production code changes of any kind — a test that surfaces a bug gets
  documented, not "fixed" and not weakened to tolerate the bug silently.
- No changes to the existing 163-test internal suite, testkit, or WireMock
  bases — this mission is purely additive.
- No dependency on `qa-commons-template` — it's example/reference code, not
  a contract this repo should couple to.
- No CI wiring for the live suite — it stays a local, manual, opt-in run via
  `-DrunLive=true`, exactly like the existing gating.
- No idempotency/dedup implementation. The duplicate-send test pins *today's*
  behavior (two 202s, two distinct ids) as a deliberate tripwire: if
  idempotency ever ships, this test starts failing and that's the alarm.
- No Allure/reporting wiring. qa-commons-core v0.3.0 carries a reflective
  `ReportContextExtension`/`AllureReporterBridge` transitively, but nothing
  in this mission asks for it — left un-adopted rather than half-wired.

## Design

### Dependency bump

`pom.xml`: bump `qa-commons-api` `v0.1.0` → `v0.3.0`; add `qa-commons-db`
`v0.3.0`, `test` scope, same JitPack groupId
(`com.github.UseYourActive.qa-commons`). `Endpoint`/`ApiResult`/`QaConfig`
are source-compatible between v0.1.0 and v0.3.0 (verified against the local
qa-commons checkout at that tag) — the existing `ChannelsEndpoint` and its
test need no changes.

### Package layout (all new, all under `src/test/java`)

```
bg.sit_varna.sit.si.qacommons/
  ChannelsEndpoint.java                      (existing, untouched)
  ChannelsQaCommonsConsumptionTest.java       (existing, untouched — already
                                                covers channels contract shape)
  NotificationsEndpoint.java                  (new) POST /api/v1/notifications/send
  FailedNotificationsEndpoint.java            (new) GET  /api/v1/notifications/failed
  FailedNotificationsPageResponse.java        (new) envelope shim, see below
  MetricsEndpoint.java                        (new) GET  /api/v1/metrics/today
  SendNotificationLiveTest.java               (new) positive + validation negative
  SendNotificationOracleLiveTest.java         (new) send + DB row oracle
  DuplicateSendLiveTest.java                  (new) independence, not idempotency
  FailedNotificationsContractLiveTest.java    (new) envelope contract shape
  MetricsTodayContractLiveTest.java           (new) envelope contract shape
  InvalidChannelFindingLiveTest.java          (new) pins the known 500 bug
  db/
    NotificationsOracle.java                  (new) findById(id) → NotificationRow
    NotificationRow.java                      (new) id/recipient/channel only
    SchemaFingerprint.java                    (new) once-per-run migration check
```

Every `Endpoint<TReq, TRes, TErr>` subclass is typed against this repo's own
production records — `SendNotificationRequest`/`SendNotificationResponse`,
`GetChannelsResponse`, `FailedNotificationResponse`, `ErrorResponse` — the
same pattern the existing `ChannelsEndpoint` established, not
`qa-commons-template`'s parallel copies of those shapes.

### Endpoint-to-DTO mapping

| Endpoint class | Path | `TReq` | `TRes` | `TErr` |
|---|---|---|---|---|
| `ChannelsEndpoint` (existing) | `GET /api/v1/channels` | `Void` | `GetChannelsResponse` | `ErrorResponse` |
| `NotificationsEndpoint` | `POST /api/v1/notifications/send` | `SendNotificationRequest` | `SendNotificationResponse` | `ErrorResponse` |
| `FailedNotificationsEndpoint` | `GET /api/v1/notifications/failed` | `Void` | `FailedNotificationsPageResponse` | `ErrorResponse` |
| `MetricsEndpoint` | `GET /api/v1/metrics/today` | `Void` | `Map` (raw) | `ErrorResponse` |

Two spots need explicit judgment calls, both flagged for sign-off:

**`FailedNotificationsPageResponse` (generics-erasure shim).** The real
response is `PageResponse<FailedNotificationResponse>`
(`items`/`page`/`size`/`totalItems`/`totalPages`), but qa-commons'
`Endpoint` constructor only accepts a raw `Class<TRes>`
(`Endpoint.java:41`), and Jackson's `readValue(String, Class<T>)` erases
`T` — deserializing straight into `PageResponse.class` would silently
produce `items: List<LinkedHashMap>` instead of
`List<FailedNotificationResponse>`. `FailedNotificationsPageResponse` is a
test-local, non-generic record with the identical field set, using the real
`FailedNotificationResponse` for `items`' element type — a concrete stand-in
for the erased generic envelope, not a new invented contract. This is the
one place this suite deviates from "type directly against the production
record."

**`MetricsEndpoint` typed as raw `Map`.** `MetricsResource` returns a raw
`Map<String, Object>` — there is no production DTO to type against (see
`MetricsApi.java:113-116`, `implementation = Object.class`). The live test
asserts structurally (`total` numeric, `byChannel` map-shaped,
`successRate` numeric) rather than against field types that don't exist yet.

### Validation negative + known-bug finding

`SendNotificationLiveTest` sends a request with a blank `recipient`
(`SendNotificationRequest` has no constructor-time validation — Bean
Validation runs at the JAX-RS boundary) and asserts
`result.expectFailure().code().equals("VALIDATION_FAILED")` at 400, per
`ConstraintViolationExceptionMapper` — the same mapper the internal
`NotificationResourceTest.testSendEndpoint_ValidationFailure` already
exercises, now proven from outside the process.

`InvalidChannelFindingLiveTest` sends a request with an unrecognized
`channel` value. No exception mapper in `exception/mapper/` catches a
Jackson deserialization failure (only `ValidationException`,
`ConstraintViolationException`, `IllegalArgumentException`,
`NotificationException`, and rate-limiting are handled), so this currently
surfaces as an unhandled 500 — `ResultClassifier` can't parse a stock
Quarkus error page as `ErrorResponse`, so `ApiResult` comes back
`Unparsed(status=500, ...)`. The test pins exactly that: `status() == 500`
and `Unparsed`, with a comment naming it a known finding, not the desired
behavior.

`docs/specs/qa-commons-live-suite/findings.md` records three findings, each
added in the task that surfaces it:
1. **Service bug** — invalid `channel` → unhandled 500 (repro, expected 400
   vs actual 500, evidence: `InvalidChannelFindingLiveTest`).
2. **qa-commons framework gap** — `Endpoint`'s raw `Class<TRes>` can't
   express a generic envelope like `PageResponse<T>`, forcing the
   `FailedNotificationsPageResponse` shim. The real fix (a
   `TypeReference`/`JavaType`-accepting overload) belongs in qa-commons's
   own backlog; this entry is the evidence for that upstream ask, not
   something this branch fixes.
3. **Service convention gap** — `MetricsResource` returns a raw `Map` with
   no production DTO, which contradicts this codebase's own convention of
   typed response records everywhere else. A typed `MetricsResponse` record
   is future service work, out of scope for this branch.

### DB oracle

`NotificationsOracle`/`NotificationRow` mirror the shape qa-commons' own
`template` module demonstrates (`NotificationsOracle.findById`,
`NotificationRow(id, recipient, channel, lockedBy, lockedAt,
attemptsCount)`) — reimplemented locally against `qa-commons-db`'s
table-agnostic `PostgresDatabase`/`RowMapper`, not imported from
`qa-commons-template`. `SendNotificationOracleLiveTest` asserts identity
only (`id`, `recipient`, `channel` match what was sent) — never
`status`/`locked_by`/`locked_at` values, since the durable-queue poller can
claim the row at any point after intake (race, not a bug).

`SchemaFingerprint.verifyOnce(DbConfig)` queries
`SELECT version FROM flyway_schema_history WHERE success = true` and asserts
it contains `{1.0.0, 1.0.1, 1.0.2, 1.0.3}` (this repo's actual migrations —
`src/main/resources/db/migrations/V1.0.0`–`V1.0.3`) before any row assertion
runs, guarded by an `AtomicBoolean` so it executes once even if multiple
test classes call it. This is what stands between "the row I found" and "the
row I found in some other database that happens to be listening on
`localhost:5432`."

**Open question, resolved provisionally:** `DbConfig.fromEnv()`'s defaults
(`localhost:5432/notificationdb`, `postgres`/`postgres`) match this
service's `.env.example` on host, db name, user, and password — but not
port. This repo's actual `.env` remaps the compose Postgres to host port
**15432** (`DB_PORT=15432`), not 5432. Running the oracle tests from the
host therefore requires `QA_DB_PORT=15432` set explicitly; it is not a
zero-config match despite the mission's premise. This gets one line in the
README run instructions, not a code change (`qa-commons-db` is out of our
control).

### Allure output hygiene

qa-commons-core carries `allure-java-commons` test-scoped for its own
reflective `AllureReporterBridge` (doesn't propagate transitively — Maven
test-scope deps stop at the direct consumer). This repo isn't wiring Allure
reporting in this mission (see Non-goals), but a stray root-level
`allure-results/` directory was already present at the start of this work,
left over from an earlier local run outside this session. Forward-proofing
against any future Allure listener writing to the working directory instead
of `target/`: surefire gets `-Dallure.results.directory=${project.build.directory}/allure-results`,
`allure-results/` goes in `.gitignore`, and the stray root folder is
deleted. Belt-and-suspenders, not a functional change — nothing in this
mission currently produces Allure output.

### Contract re-verification (execution-time activity, not code)

Before locking each endpoint's typed shape, start the service
(`docker-compose up`) and diff `/q/openapi` against the assumed DTOs from
the research above. Any drift found gets folded into this plan before the
dependent test is written, not discovered after.

### README

One paragraph in the existing `## 🧪 Testing & Validation` section, matching
its current terse/imperative tone: the internal suite (`@QuarkusTest`,
WireMock, testkit) verifies behavior from inside the process; the
`qacommons`-tagged `@Tag("live")` suite verifies the deployed HTTP+DB
contract from outside, via qa-commons, opt-in with the same
`-DrunLive=true` flag already documented.

## Risks & open questions

- **Branch-base detour (resolved this session):** the mission said "branch
  off master," but master didn't yet contain the v0.1.0 proof commit — it
  was only on `feature/qa-commons-consumer-proof`, unmerged. Per explicit
  direction, that branch was merged into `master` with a merge commit
  (`41cce6e`), CI confirmed green with the live test correctly gated
  (`./mvnw verify`, no `-DrunLive`/`-Dgroups=live` override), and
  `feature/qa-commons-live-suite` branches from that updated `master`.
- **`FailedNotificationsPageResponse` shim** — flagged above; it's an
  erasure workaround, not a new contract. Worth explicit sign-off since the
  mission emphasizes typing directly against production records.
- **`QA_DB_PORT` mismatch** — defaults don't fully match this repo's actual
  compose port remap (15432 vs 5432 default); documented as a required env
  var, not treated as blocking.
- **Metrics has no typed production DTO** — typed as raw `Map` by design;
  if `MetricsResource` ever gains a real response record, `MetricsEndpoint`
  should be retyped to match (follow-up, not this mission).
- **Invalid-channel test pins a bug on purpose** — if the Jackson
  deserialization gap ever gets a mapper, this test will start failing and
  will need its assertion updated to 400/`ErrorResponse`. That's the
  intended tripwire, same philosophy as the duplicate-send test.
