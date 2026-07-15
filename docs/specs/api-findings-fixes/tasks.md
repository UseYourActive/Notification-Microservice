# Tasks: api-findings-fixes

- [x] T1: `JsonbDeserializationExceptionMapper` + `JacksonDeserializationExceptionMapper`
  + shared response-building helper + regression tests — files:
  `src/main/java/bg/sit_varna/sit/si/exception/mapper/{JsonbDeserializationExceptionMapper,JacksonDeserializationExceptionMapper,DeserializationErrorTranslator}.java`,
  `src/test/java/bg/sit_varna/sit/si/api/NotificationResourceTest.java` (new
  integration test method for the JSON-B path — today's actual winner),
  `src/test/java/bg/sit_varna/sit/si/exception/mapper/JacksonDeserializationExceptionMapperTest.java`
  (new unit test, directly constructs an `InvalidFormatException` — nothing
  currently routes request bodies through Jackson, so this path can't be
  reached via `@QuarkusTest`) — done when: a request with
  `"channel":"CARRIER_PIGEON"` returns 400 `VALIDATION_FAILED` with a detail
  naming `NotificationChannel` and its accepted values (integration test +
  live against the running service); the Jackson-path unit test proves
  `getPath()`'s literal field name is used when that path is ever reached;
  163 internal tests still green.

- [x] T2: Fix `NotificationApi`'s `POST /send` `@APIResponse` — `200` → `202`
  — files: `src/main/java/bg/sit_varna/sit/si/controller/api/NotificationApi.java`
  — done when: `/q/openapi` on the running service shows `202` for that
  operation's success response and no other response codes changed.

- [x] T3: `MetricsResponse` record, wire into `MetricsResource`, update
  `MetricsApi`'s OpenAPI schema — files:
  `src/main/java/bg/sit_varna/sit/si/dto/response/MetricsResponse.java`,
  `src/main/java/bg/sit_varna/sit/si/controller/resource/MetricsResource.java`,
  `src/main/java/bg/sit_varna/sit/si/controller/api/MetricsApi.java` — done
  when: the live `GET /api/v1/metrics/today` JSON body is byte-for-byte the
  same shape as before (checked live), `/q/openapi` shows a real schema
  (not `implementation = Object.class`), and the existing
  `MetricsResourceApiTest` passes unchanged.

- [x] T4: Update the live acceptance test — rename
  `InvalidChannelFindingLiveTest` → `InvalidChannelValidationLiveTest`,
  assert the new 400/`VALIDATION_FAILED`/`expectFailure()` behavior — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/InvalidChannelValidationLiveTest.java`
  (renamed from `InvalidChannelFindingLiveTest.java`) — done when: this test
  passes against the running service post-fix (fails against a pre-fix
  checkout, confirming it actually pins the new behavior), and the other 7
  live tests pass unchanged.

- [x] T5: Update `findings.md` — mark all three entries resolved with commit
  references, add finding #4 (dual JSON-B/Jackson reader race, discovered
  while fixing #1, left open/unresolved on purpose) — files:
  `docs/specs/qa-commons-live-suite/findings.md` — done when: entries 1-3
  each state the fix commit and the proof (T1/T4's regression and live
  tests), and entry 4 documents the reader race as a future, separate
  consolidation mission.

- [x] T6: Close-out — both gates — files:
  `docs/specs/api-findings-fixes/tasks.md` (check off all boxes) — done
  when: `mvn test` (service down) is green (163 internal tests, live tests
  excluded) and `mvn test -DrunLive=true` (service up) is green (8 live
  tests, including the renamed acceptance test).
