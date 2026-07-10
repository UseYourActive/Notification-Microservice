# Tasks: test-kit-adapters

- [ ] T1: Add `wiremock-standalone` and `assertj-core` test-scope dependencies — files:
  `pom.xml` — done when: `mvn dependency:tree` lists both, project compiles (`mvn test-compile`).

- [ ] T2: Add configurable provider base-URL to the three channel configs, defaulting to
  today's hardcoded values (no prod behavior change) — files:
  `config/channel/TelegramConfig.java`, `config/channel/TwilioConfig.java`,
  `config/channel/SendGridConfig.java`, `service/channel/telegram/TelegramApiSender.java`,
  `service/channel/sms/TwilioSmsSender.java`, `service/channel/email/SendGridEmailSender.java`
  — done when: existing sender tests/build still pass unchanged and each sender builds its
  request URL from the new config value instead of the hardcoded constant.

- [ ] T3: Create `testkit.base.DatabaseTestBase` (reuses `TestResources`, adds
  truncate-between-tests) — files: `testkit/base/DatabaseTestBase.java` — done when: a
  throwaway smoke test extending it shows no row leakage across two test methods.

- [ ] T4: Create `testkit.wiremock.WireMockLifecycleManager` + `WireMockTestBase` (shared
  WireMock instance, config overrides for the T2 base-URL properties, `resetAll()` per test)
  — files: `testkit/wiremock/WireMockLifecycleManager.java`,
  `testkit/wiremock/WireMockTestBase.java` — done when: a throwaway smoke test stubs an
  endpoint and a plain HTTP call through the overridden config reaches it.

- [ ] T5: Create `testkit.base.ApiTestBase` (RestAssured defaults, extends
  `DatabaseTestBase`) and `testkit.assertions.ErrorResponseAssert` (matching the real
  `ErrorResponse` fields: code/title/message/category/timestamp/details) — files:
  `testkit/base/ApiTestBase.java`, `testkit/assertions/ErrorResponseAssert.java` — done
  when: a throwaway test asserts a known error response via `assertThatError(...)`.

- [ ] T6: Create object mothers (`NotificationRecordMother`, `NotificationAttemptMother`,
  `TemplateRecordMother`) and `testkit.assertions.NotificationAssert` — files:
  `testkit/mother/NotificationRecordMother.java`,
  `testkit/mother/NotificationAttemptMother.java`, `testkit/mother/TemplateRecordMother.java`,
  `testkit/assertions/NotificationAssert.java` — done when: each mother compiles with
  sensible defaults and `NotificationAssert` is exercised by a throwaway test.

- [ ] T7: Refactor 3 existing tests onto the kit as proof (no other tests touched) — files:
  `repository/NotificationRepositoryTest.java` (→ `DatabaseTestBase` +
  `NotificationRecordMother`), `api/NotificationResourceTest.java` (→ `ApiTestBase` +
  `ErrorResponseAssert` + `NotificationAssert`), `service/core/NotificationStateServiceTest.java`
  (→ `NotificationRecordMother` + `NotificationAssert`) — done when: all three pass using
  kit components for setup/assertions instead of their old inline helpers.

- [ ] T8: Telegram sender integration test against WireMock — files:
  `service/channel/telegram/TelegramApiSenderIntegrationTest.java` — done when: it covers
  the success path (asserts the real request body/headers WireMock received), and provider
  error paths (400/403/429/5xx) each asserting the specific `TelegramSendException` +
  `NotificationErrorCode` thrown.

- [ ] T9: SMS (Twilio) sender integration test against WireMock — files:
  `service/channel/sms/TwilioSmsSenderIntegrationTest.java` — done when: it covers the
  success path (asserts form fields `From`/`To`/`Body` and the Basic auth header WireMock
  received) and the provider-error path asserting `SmsSendException`.

- [ ] T10: Email (SendGrid) sender integration test against WireMock — files:
  `service/channel/email/SendGridEmailSenderIntegrationTest.java` — done when: it covers
  the success path (asserts the JSON mail payload WireMock received) and provider-error
  paths (400/401/429/5xx) each asserting the specific `EmailSendException` +
  `NotificationErrorCode`. If the SendGrid base-URI seam from T2 doesn't hold up, fall back
  per plan.md's documented alternative and note the deviation here before continuing.

- [ ] T11: `template.loading` coverage — files: `template/loading/TemplateFileParserTest.java`
  (unit), `template/loading/TemplateScannerTest.java` (unit, against a test-fixture
  `templates/` resource dir), `template/loading/TemplateLoaderTest.java` (slice,
  `@QuarkusTest`) — done when: all three pass and cover the happy path plus at least one
  malformed-input/not-found case per class.

- [ ] T12: API/contract tests for `ChannelResource` and `MetricsResource` — files:
  `api/ChannelResourceApiTest.java`, `api/MetricsResourceApiTest.java` — done when: both
  cover status codes and response body shape via `ApiTestBase`.

- [ ] T13: API/contract tests for `TemplateResource` — files:
  `api/TemplateResourceApiTest.java` — done when: all 7 endpoints
  (`validate`/`discovery`/create/list/get/update/delete) are covered for success, validation
  (400), and not-found (404) cases, error bodies asserted via `ErrorResponseAssert`.

- [ ] T14: API/contract tests for `WebhookResource` — files:
  `api/WebhookResourceApiTest.java` — done when: valid-signature (200) and
  invalid-signature (4xx, error body via `ErrorResponseAssert`) paths are both covered.

- [ ] T15: Before/after coverage report — files: `docs/specs/test-kit-adapters/plan.md`
  (append results) or a new `docs/audits/` entry — done when: `mvn verify` jacoco output is
  re-extracted for every package touched above (T2, T8-T14) and tabulated next to the
  before-numbers already in plan.md.
