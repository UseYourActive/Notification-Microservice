# api-findings-fixes

## Problem

`docs/specs/qa-commons-live-suite/findings.md` names three gaps the live
suite surfaced: an invalid `channel` value crashes with a 500 instead of a
400, `POST /send`'s OpenAPI doc claims `200` when the service returns `202`,
and `metrics-today` has no typed response DTO. All three are fixable without
touching behavior clients already depend on.

## Goal / Non-goals

**Goals**
- Unhandled-deserialization-failure → 500 becomes a 400 `VALIDATION_FAILED`,
  same shape as every other validation failure.
- `NotificationApi`'s `@APIResponse` for `POST /send` says `202`, matching
  reality.
- `MetricsResource` returns a typed `MetricsResponse` record instead of a
  raw `Map`, wire JSON unchanged.
- `InvalidChannelFindingLiveTest` — which pins the *old* 500 on purpose —
  gets updated to assert the *new* 400 and renamed; it's this mission's
  acceptance check, not incidental cleanup.
- `findings.md` marks all three resolved with commit references.

**Non-goals**
- No change to the dual JSON-B/Jackson reader setup itself (see below) —
  out of scope, and not what was asked.
- No rewrite of `NotificationApi`'s `200` response description prose beyond
  the status code — the "delivery confirmed" wording is also arguably
  stale, but that's scope creep past "tell the truth about the code."
- No changes to the other 7 live tests, the 163-test internal suite's
  existing assertions, or any file outside this mission's three findings.

## Design

**Reality check on finding #1's premise.** The mission (and findings.md)
assumed a Jackson deserialization failure (`InvalidFormatException`). Live
reproduction against the running service shows otherwise — this repo has
*both* `quarkus-rest-jsonb` and `quarkus-rest-jackson` on the classpath
(`pom.xml:74,82`, no comment explaining the coexistence), and for this
request `RequestDeserializeHandler` routes through `JsonbMessageBodyReader`
(Yasson), not Jackson. The captured stack trace:

```
jakarta.json.bind.JsonbException: Internal error: No enum constant
bg.sit_varna.sit.si.constant.NotificationChannel.CARRIER_PIGEON
Caused by: java.lang.IllegalArgumentException: No enum constant ...
```

So the new mapper targets `jakarta.json.bind.JsonbException`, not a Jackson
type. This is a "no other production changes" -compliant fix — it handles
the exception actually thrown, without touching *why* JSON-B wins the
reader race (that's a separate, unscoped architectural question).

**Widened per review: two mappers, not one.** The current reader-race
winner (JSON-B) is an accident, not a design decision — if the dual JSON
stack is ever consolidated (likely toward Jackson, the default everywhere
else in this codebase), a `JsonbException`-only mapper would silently stop
matching and the 500 would come back. So this adds **two** thin mappers
sharing one response-building helper:

- **`JsonbDeserializationExceptionMapper`** (`ExceptionMapper<JsonbException>`)
  — handles today's actual winner. Reflectively reads the offending enum's
  `getEnumConstants()` off the class named in the root cause's message via
  a regex on `"No enum constant (.+)\.(\w+)"` — generic across any enum,
  not hardcoded to `NotificationChannel`.
- **`JacksonDeserializationExceptionMapper`** (`ExceptionMapper<InvalidFormatException>`)
  — dormant today (nothing currently routes through Jackson for request
  bodies), but exercised directly by a unit test so it's proven correct
  before it's ever needed. Uses `InvalidFormatException.getPath()` for the
  literal JSON field name and `getTargetType()` for accepted enum values.

Both fall back to a generic 400 (exception's own message, no enum-specific
detail) for non-enum shapes, so any format/type mismatch is a 400 now, not
just bad enums.

**Field-name asymmetry, intentional and documented.** Jackson's
`InvalidFormatException` retains a JSON property path; `JsonbException`/
Yasson does not — only the target enum's fully-qualified class name and the
rejected value. So the Jackson-path detail names the literal JSON field
(`"channel"`); the JSON-B-path detail names the enum type instead
(`"NotificationChannel"`): `"Invalid value 'CARRIER_PIGEON' for
NotificationChannel; accepted values: EMAIL, SMS, TELEGRAM"`. The message's
job is actionability, not a guaranteed field path — this works whether or
not a DTO has exactly one enum field, and is called out with one code
comment noting that consolidating onto Jackson later (finding #4) improves
this for free. No custom Yasson deserializer to force a field path — that's
production complexity bought for an error-message nicety, not worth it in a
findings-fix mission.

**`MetricsResponse`** (new record, `dto/response/`): `long total`,
`Map<String, Long> byChannel`, `double successRate` — matches
`MetricsService.getTodayTotal()/getTodayByChannel()/getTodaySuccessRate()`
exactly (checked against the method signatures, not just the wire sample).
Same component names as the current `Map` keys, so Jackson serializes an
identical JSON shape — the existing `MetricsResourceApiTest` (internal
suite) needs no changes.

**Live-suite acceptance.** `InvalidChannelFindingLiveTest` →
`InvalidChannelValidationLiveTest`, method renamed off "...500...", body
swapped to `expectFailure()`/`code()==VALIDATION_FAILED`/400, using the
same raw-JSON RestAssured approach (still needed — `NotificationChannel` is
still a closed enum, the typed `Endpoint` still can't express an invalid
value). The other 7 live tests are untouched files.

## Risks & open questions

- **Enum type name vs. JSON field name** in the `JsonbException` path —
  flagged above; accepted as the best available identifier given
  `JsonbException`'s API. The Jackson path does have the real field name,
  used there.
- **Dual JSON-B/Jackson setup** is pre-existing and stays as-is in this
  mission — not touched, not consolidated. Becomes **finding #4** in
  `findings.md` (new, left open/unresolved): two JSON stacks racing for
  MessageBodyReader priority is nondeterministic behavior surface and
  extra native-image weight; consolidation is its own future mission with
  real blast radius (naming/null-handling semantics may differ per stack).
- **`findings.md` commit references** can only be written after the fix
  commits exist, so that task runs last, right before close-out.
