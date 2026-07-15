# Tasks: qa-commons-live-suite

- [ ] T1: Bump `qa-commons-api` to v0.3.0, add `qa-commons-db` v0.3.0 (test
  scope) — files: `pom.xml` — done when: `mvn dependency:tree` shows both at
  v0.3.0, `mvn test` (service down) stays green with `ChannelsQaCommonsConsumptionTest`
  reported skipped/excluded (live-tag gating unaffected).

- [ ] T2: Relocate Allure output — surefire gets
  `-Dallure.results.directory=${project.build.directory}/allure-results`,
  `allure-results/` added to `.gitignore`, stray root `allure-results/`
  folder deleted (`git rm --cached` first only if it was ever tracked — it
  isn't) — files: `pom.xml`, `.gitignore` — done when: repo root has no
  `allure-results/` directory and a test run (`mvn test`) doesn't recreate
  one at root.

- [ ] T3: Start the service via `docker-compose up`, fetch `/q/openapi`, and
  diff it against the assumed contract for send/channels/failed/metrics
  (paths, status codes, DTO field names) captured in plan.md's research —
  files: `docs/specs/qa-commons-live-suite/contract-verification.md` — done
  when: each of the 4 endpoints' shape is confirmed or drift is recorded and
  folded back into plan.md before T5+ proceed.

- [ ] T4: Add the DB oracle — `NotificationsOracle`, `NotificationRow`,
  `SchemaFingerprint` — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/db/{NotificationsOracle,NotificationRow,SchemaFingerprint}.java`
  — done when: `SchemaFingerprint.verifyOnce(DbConfig)` compiles and, run
  manually against the local compose Postgres (`QA_DB_PORT=15432`), confirms
  `flyway_schema_history` contains `1.0.0`–`1.0.3` and throws on a
  deliberately wrong `QA_DB_NAME`.

- [ ] T5: Add `NotificationsEndpoint` (send) — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/NotificationsEndpoint.java` —
  done when: it compiles against `SendNotificationRequest`/
  `SendNotificationResponse`/`ErrorResponse` and `mvn test-compile` passes.

- [ ] T6: `SendNotificationLiveTest` — positive (202, response shape) and
  validation negative (blank recipient → 400 `VALIDATION_FAILED` via
  `expectFailure`) — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/SendNotificationLiveTest.java`
  — done when: both pass against the running service
  (`mvn test -DrunLive=true`).

- [ ] T7: `SendNotificationOracleLiveTest` — send, then assert the row
  exists with matching `id`/`recipient`/`channel` (no status/lock-value
  assertions) — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/SendNotificationOracleLiveTest.java`
  — done when: it passes against the running service + Postgres, and fails
  loudly (not silently) if `QA_DB_*` points at the wrong database.

- [ ] T8: `DuplicateSendLiveTest` — two identical payloads → two 202s,
  distinct `notificationId`s — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/DuplicateSendLiveTest.java` —
  done when: it passes against the running service and its Javadoc states
  it documents current behavior, not a guarantee.

- [ ] T9: `InvalidChannelFindingLiveTest` + findings doc (entry 1 of 3) —
  pins the current unhandled-500 behavior for an invalid `channel` value,
  never weakened to expect 400 — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/InvalidChannelFindingLiveTest.java`,
  `docs/specs/qa-commons-live-suite/findings.md` — done when: the test
  passes against the running service (asserts `Unparsed`, status 500) and
  the findings doc names the gap (no Jackson-deserialization exception
  mapper) without proposing a production fix in this branch.

- [ ] T10: `FailedNotificationsEndpoint` + `FailedNotificationsPageResponse`
  shim + `FailedNotificationsContractLiveTest` — envelope contract shape,
  tolerating an empty `items` list; findings doc entry 2 of 3 (qa-commons
  `Endpoint`'s raw `Class<TRes>` can't express `PageResponse<T>`, forcing
  the shim — noted as an upstream qa-commons backlog item, not fixed here)
  — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/{FailedNotificationsEndpoint,FailedNotificationsPageResponse,FailedNotificationsContractLiveTest}.java`,
  `docs/specs/qa-commons-live-suite/findings.md` — done when: the test
  passes against the running service whether or not any failed
  notifications currently exist, and findings.md has its second entry.

- [ ] T11: `MetricsEndpoint` + `MetricsTodayContractLiveTest` — envelope
  contract shape (`total`, `byChannel`, `successRate` present with expected
  types); findings doc entry 3 of 3 (`MetricsResource` returns a raw `Map`
  with no production DTO, contradicting this codebase's own typed-DTO
  convention — future service work, not this branch) — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/{MetricsEndpoint,MetricsTodayContractLiveTest}.java`,
  `docs/specs/qa-commons-live-suite/findings.md` — done when: the test
  passes against the running service and findings.md has all 3 entries.

- [ ] T12: README two-tier testing paragraph — files: `README.md` — done
  when: the `## 🧪 Testing & Validation` section states the internal vs.
  live-suite split in one paragraph, matching the section's existing tone.

- [ ] T13: Close-out verification — run `mvn test` with the service down
  (must stay green, live tests excluded), `mvn test -DrunLive=true` with the
  service + Postgres up (full live suite green except the intentional T9
  finding-pin, which passes by pinning the bug), and confirm the repo root
  contains no `allure-results/` directory after that live run — files:
  `docs/specs/qa-commons-live-suite/tasks.md` (check off all boxes) — done
  when: all three checks are confirmed and this file has no unchecked boxes
  without a documented reason.
