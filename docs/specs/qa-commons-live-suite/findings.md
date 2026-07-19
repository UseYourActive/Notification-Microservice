# Findings — qa-commons-live-suite

Things the live suite surfaced that are **not fixed or worked around in this
branch**, per the mission's constraint: a test that reveals a gap gets
documented, not silently weakened to tolerate it and not fixed as a
drive-by. Each entry names the evidence test and, where relevant, whose
backlog the fix belongs on.

## 1. Service bug: invalid `channel` value → unhandled 500 — **RESOLVED**

**Expected:** `400` with an `ErrorResponse` body (`VALIDATION_FAILED` or
similar), consistent with every other input-validation failure on this
endpoint.

**Actual (before the fix):** `500 Internal Server Error` with a plain-text
Quarkus error page (not JSON).

**Root cause, corrected:** the original write-up assumed Jackson. Live
reproduction during the fix (`docs/specs/api-findings-fixes/plan.md`) showed
otherwise: this app has *both* `quarkus-rest-jsonb` and `quarkus-rest-jackson`
on the classpath, and JSON-B/Yasson actually wins the `MessageBodyReader`
race for this endpoint — the real exception was `jakarta.json.bind.JsonbException`
wrapping `IllegalArgumentException: No enum constant ...`, not a Jackson
type. See finding #4 below for the reader-race itself, left open.

**Fix:** `JsonbDeserializationExceptionMapper` (the actual reader, today) +
`JacksonDeserializationExceptionMapper` (dormant, but kept correct in case
the reader race ever resolves differently) — both translate into the
existing `400 VALIDATION_FAILED` `ErrorResponse` shape, with a detail naming
the offending enum type and its accepted values.

**Commits:** `fceb91b` (mappers + regression tests),
`b84ad6e` (live acceptance test — renamed `InvalidChannelFindingLiveTest` →
`InvalidChannelValidationLiveTest`, now asserts 400 instead of pinning 500).

**Proof:** `NotificationResourceTest.testSendEndpoint_InvalidChannelValue_ReturnsValidationFailed`
(integration, JSON-B path), `JacksonDeserializationExceptionMapperTest` (unit,
Jackson path), `InvalidChannelValidationLiveTest` (live, post-fix).

## 2. qa-commons framework gap: `Endpoint` can't express a generic envelope — **RESOLVED**

**Expected:** `FailedNotificationsEndpoint` types directly against the real
production response, `PageResponse<FailedNotificationResponse>`, the same
way every other endpoint in this suite types against its real DTO.

**Actual (before the fix):** qa-commons' `Endpoint<TReq, TRes, TErr>`
constructor only accepted a raw `Class<TRes>` (no `TypeReference`/
`JavaType`-accepting overload). Jackson's `readValue(String, Class<T>)`
erases generic type parameters, so deserializing straight into
`PageResponse.class` would silently produce `items: List<LinkedHashMap>`
instead of `List<FailedNotificationResponse>` — a latent bug, not a compile
error. Independently confirmed at the OpenAPI level too: `/q/openapi`
itself can't resolve the generic item type either (`items: {type: array,
items: {}}`, no `$ref` — see `contract-verification.md`).

**Workaround, since removed:** `FailedNotificationsPageResponse`, a
concrete test-local record with the same fields, typed against the real
`FailedNotificationResponse` for `items`. Not a new contract — a stand-in
for the one qa-commons couldn't express yet.

**Fix:** qa-commons `v0.4.0` added an additive `Endpoint` constructor
overload accepting Jackson's `TypeReference<TRes>` alongside the existing
`Class<TRes>` one (qa-commons repo, `feature/generic-response-types`,
merged as PR #3, tag `v0.4.0`). This repo bumped `qa-commons-api`/
`qa-commons-db` from `v0.3.0` to `v0.4.0` and switched
`FailedNotificationsEndpoint` to the new constructor, typing directly
against this app's own production `PageResponse<FailedNotificationResponse>`
(`bg.sit_varna.sit.si.dto.response`) — the exact DTO `NotificationResource`
returns — instead of the deleted stand-in record. No `qa-commons-template`
dependency was added; the generic type reused is this module's own
existing production DTO, already on the test classpath via `src/main`.

**Evidence (historical):** `FailedNotificationsEndpoint`,
`FailedNotificationsPageResponse` (deleted).

**Commit:** `f10b88b` (`chore/adopt-qa-commons-v0.4.0`).

**Proof:** `FailedNotificationsContractLiveTest` (live, unchanged
assertions, now backed by the real generic type instead of the stand-in)
— green both with the service down (`mvn test`, generic-typed compile
succeeds) and with it up (`mvn test -DrunLive=true`), reading a field off
each deserialized item with no `ClassCastException`.

## 3. Service convention gap: `metrics-today` has no typed response DTO — **RESOLVED**

**Expected:** `GET /api/v1/metrics/today` returns a typed record, consistent
with every other response in this codebase (`SendNotificationResponse`,
`GetChannelsResponse`, `FailedNotificationResponse`, …).

**Actual (before the fix):** `MetricsResource.getTodayMetrics()` built and
returned a raw `Map<String, Object>` (`total`, `byChannel`, `successRate`).
The OpenAPI schema was a bare `{"type": "object"}` with no properties —
`MetricsApi`'s `@APIResponse` declared `implementation = Object.class` —
because there was nothing to introspect.

**Fix:** `MetricsResponse(long total, Map<String, Long> byChannel, double
successRate)`, field types matching `MetricsService`'s actual method
signatures (not just the wire sample). `MetricsResource` returns it
directly; `MetricsApi`'s OpenAPI schema now names the real type.
Behavior-preserving — verified live that the wire JSON shape is unchanged,
and the existing `MetricsResourceApiTest` passes without modification.

**Commit:** `f9c8bfa`.

**Proof:** `MetricsResourceApiTest` (unchanged, still passing),
`MetricsTodayContractLiveTest` (unchanged in the live suite, still passing),
live `/q/openapi` diff in the commit.

## 4. Dual JSON-B/Jackson `MessageBodyReader` race — **open, not fixed**

**Discovered while fixing #1.** This app declares both `quarkus-rest-jsonb`
(`pom.xml:74`) and `quarkus-rest-jackson` (`pom.xml:82`) as direct
dependencies, with no comment explaining the coexistence. For
`POST /api/v1/notifications/send`, RESTEasy Reactive's reader-priority
resolution currently picks JSON-B (Yasson)'s `JsonbMessageBodyReader` over
Jackson's — confirmed by the live stack trace captured while fixing finding
#1 (`docs/specs/api-findings-fixes/plan.md`). Nothing about this codebase's
usage otherwise suggests JSON-B was the intended choice — `ErrorResponse`
and other DTOs carry Jackson-specific annotations (`@JsonInclude`), and this
is the only place JSON-B's involvement was ever observed.

**Why this matters:** which reader wins is an accident of classpath/registration
order, not a design decision — nondeterministic behavior surface (differs
per endpoint, per Quarkus/RESTEasy Reactive version, possibly per build),
extra native-image weight from carrying two full JSON stacks, and two
different null-handling/naming conventions available depending on which
reader happens to fire. Finding #1's fix (`JsonbDeserializationExceptionMapper`
+ `JacksonDeserializationExceptionMapper`) is deliberately resilient to
*either* reader winning, specifically because of this.

**Fix location:** deliberately not touched in either this mission or
`api-findings-fixes`. Consolidating onto one JSON stack (likely Jackson,
the default everywhere else here) is its own future mission — real blast
radius, since serialization semantics (null-handling, date formatting,
naming) can differ subtly between the two, and every endpoint would need
re-verification, not just the one this investigation happened to touch.

## Also fixed in this mission (not originally a numbered finding here)

**`POST /send`'s OpenAPI response code was wrong.** `NotificationApi`
documented `200` for a successful send; the resource has always returned
`202` (this was recorded in `contract-verification.md`'s T3, not as a
numbered `findings.md` entry, but fixed alongside #1 and #3 since it's the
same "tell the truth about the API" mission). Status code only — the
response description prose was left alone. **Commit:** `68a7a42`. Verified
live via `/q/openapi` post-fix.
