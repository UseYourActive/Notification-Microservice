# Findings — qa-commons-live-suite

Things the live suite surfaced that are **not fixed or worked around in this
branch**, per the mission's constraint: a test that reveals a gap gets
documented, not silently weakened to tolerate it and not fixed as a
drive-by. Each entry names the evidence test and, where relevant, whose
backlog the fix belongs on.

## 1. Service bug: invalid `channel` value → unhandled 500

**Expected:** `400` with an `ErrorResponse` body (`VALIDATION_FAILED` or
similar), consistent with every other input-validation failure on this
endpoint.

**Actual:** `500 Internal Server Error` with a plain-text Quarkus error page
(not JSON).

**Root cause:** `NotificationChannel` is deserialized by Jackson from the
`channel` field of `SendNotificationRequest`. None of the `@Provider`
exception mappers in `exception/mapper/` (`ConstraintViolationExceptionMapper`,
`ValidationExceptionMapper`, `IllegalArgumentExceptionMapper`,
`NotificationExceptionHandler`, `RateLimitExceptionMapper`) catches a
Jackson deserialization failure (`InvalidFormatException`/
`JsonMappingException`), so an unrecognized enum value propagates as an
unhandled exception.

**Evidence:** `InvalidChannelFindingLiveTest` — pins the current `500`
response on purpose; verified live via direct `curl` in T3 before the test
was written.

**Fix location:** production code (`exception/mapper/`), out of scope for
this branch.

## 2. qa-commons framework gap: `Endpoint` can't express a generic envelope

**Expected:** `FailedNotificationsEndpoint` types directly against the real
production response, `PageResponse<FailedNotificationResponse>`, the same
way every other endpoint in this suite types against its real DTO.

**Actual:** qa-commons' `Endpoint<TReq, TRes, TErr>` constructor only
accepts a raw `Class<TRes>` (no `TypeReference`/`JavaType`-accepting
overload). Jackson's `readValue(String, Class<T>)` erases generic type
parameters, so deserializing straight into `PageResponse.class` would
silently produce `items: List<LinkedHashMap>` instead of
`List<FailedNotificationResponse>` — a latent bug, not a compile error.
Independently confirmed at the OpenAPI level too: `/q/openapi` itself can't
resolve the generic item type either (`items: {type: array, items: {}}`,
no `$ref` — see `contract-verification.md`).

**Workaround here:** `FailedNotificationsPageResponse`, a concrete
test-local record with the same fields, typed against the real
`FailedNotificationResponse` for `items`. Not a new contract — a stand-in
for the one qa-commons can't express yet.

**Evidence:** `FailedNotificationsEndpoint`, `FailedNotificationsPageResponse`.

**Fix location:** qa-commons's own backlog (a `TypeReference`/`JavaType`
overload on `Endpoint`), not this repo. This entry is the evidence for that
upstream ask.

## 3. Service convention gap: `metrics-today` has no typed response DTO

**Expected:** `GET /api/v1/metrics/today` returns a typed record, consistent
with every other response in this codebase (`SendNotificationResponse`,
`GetChannelsResponse`, `FailedNotificationResponse`, …).

**Actual:** `MetricsResource.getTodayMetrics()` builds and returns a raw
`Map<String, Object>` (`total`, `byChannel`, `successRate`). The OpenAPI
schema is a bare `{"type": "object"}` with no properties — `MetricsApi`'s
`@APIResponse` even declares `implementation = Object.class` — because
there's nothing to introspect.

**Consequence for this suite:** `MetricsEndpoint` is typed against raw
`Map` rather than a production record, and `MetricsTodayContractLiveTest`
asserts structurally (keys present, plausible types) instead of against
real field types.

**Evidence:** `MetricsEndpoint`, `MetricsTodayContractLiveTest`.

**Fix location:** production code — a typed `MetricsResponse` record — is
future service work, out of scope for this branch.
