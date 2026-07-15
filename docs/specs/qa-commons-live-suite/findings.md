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
