# Test-kit and adapter-layer coverage

## Problem

The adapter layer (Telegram, SMS, email channel senders) sits at 0-2% branch coverage
because there is no fixture for testing an outbound HTTP adapter without either hitting
the real provider or writing a mock-based test that only asserts "the mock was called."
`controller.resource` is 32.7% line / 0% branch overall, with four of its five resources
(`ChannelResource`, `MetricsResource`, `TemplateResource`, `WebhookResource`) essentially
untested at the HTTP layer. `template.loading` is 0% across the board. Every existing test
also builds its own fixtures inline (entities, RestAssured setup, error-body checks), so
each new test either duplicates that setup or skips it.

## Goal / Non-goals

**Goals**
- A reusable `testkit` package: DB test base, WireMock test base, API test base, object
  mothers for the core aggregates, and custom assertions for `NotificationRecord` and the
  service's error-response shape.
- Integration tests for the three channel senders (Telegram, SMS/Twilio, email/SendGrid)
  against WireMock fakes, covering success and provider-error paths, asserting the actual
  request shape sent and the exception thrown.
- Coverage for `template.loading` (unit/slice per the taxonomy) and the four uncovered
  `controller.resource` classes (API/contract tests: status codes, error bodies, validation).
- Proof the kit works by migrating 2-3 existing tests onto it.
- A before/after branch-coverage table per touched package.

**Non-goals**
- Migrating the other 100+ existing tests onto the kit. Only 2-3 move, as proof.
- Replacing `BaseIntegrationTest`/`TestResources` (the existing Testcontainers Postgres+Redis
  resource manager) — the kit's `DatabaseTestBase` reuses `TestResources` rather than
  reinventing container lifecycle.
- Implementing true RFC 7807 `application/problem+json`. The service's actual error shape
  is a custom `ErrorResponse` record (`code`, `title`, `message`, `category`, `timestamp`,
  `details`) — see Risks below. The kit's error assertion targets that real shape.
- Touching `TelegramNotificationStrategy` / `SmsNotificationStrategy` / `EmailNotificationStrategy`
  (the dispatch layer above the senders) — this spec covers the senders themselves, not
  their callers.

## Design

### testkit package layout (`src/test/java/bg/sit_varna/sit/si/testkit/`)

```
testkit/
  base/
    DatabaseTestBase.java       // @QuarkusTestResource(TestResources.class) + truncate-between-tests
    ApiTestBase.java            // RestAssured base config, extends DatabaseTestBase
  wiremock/
    WireMockLifecycleManager.java  // QuarkusTestResourceLifecycleManager, starts 1 shared WireMockServer
    WireMockTestBase.java          // @QuarkusTestResource(WireMockLifecycleManager.class), resetAll() per test
  mother/
    NotificationRecordMother.java
    NotificationAttemptMother.java
    TemplateRecordMother.java
  assertions/
    NotificationAssert.java     // AssertJ custom assertion for NotificationRecord
    ErrorResponseAssert.java    // AssertJ custom assertion for the service's ErrorResponse shape
```

**DatabaseTestBase** — wraps the existing `TestResources` (Testcontainers Postgres+Redis,
Flyway `migrate-at-start=true`) that `BaseIntegrationTest` already uses, and adds
`@BeforeEach`/`@AfterEach` truncation of `notifications`, `notification_attempts`,
`templates` via Panache `deleteAll()` so tests don't leak rows across methods. Rejected
alternative: a fresh container per test class — too slow given container startup cost;
truncate-between-tests gets isolation without paying for a new container per test.

**WireMockTestBase + WireMockLifecycleManager** — Quarkus fixes config at boot, so the
WireMock port must be known before the app starts. `WireMockLifecycleManager` implements
`QuarkusTestResourceLifecycleManager`, starts one `WireMockServer` on a dynamic port in
`start()`, and returns config overrides for all three provider base-URLs pointed at that
one instance (see production seam below). `WireMockTestBase` exposes the shared
`WireMockServer` via a static accessor and resets stubs in `@BeforeEach`. One shared
instance (not one per provider) because Quarkus test-resource config is fixed per test
class anyway, and the three providers use disjoint path prefixes so stubs don't collide.

**ApiTestBase** — RestAssured defaults (base path, content-type, failure-logging — currently
duplicated ad hoc in `BaseIntegrationTest`), extends `DatabaseTestBase` so API tests get
both. Existing `BaseIntegrationTest` is left as-is (used by the ~15+ tests not touched here);
`ApiTestBase` is additive, not a replacement.

**Object mothers** — one per aggregate (`NotificationRecordMother`, `NotificationAttemptMother`,
`TemplateRecordMother`), fluent builders with defaults, e.g.
`aNotification().queued().build()`, `aNotification().failed().withAttempts(3).build()`,
`aTemplate().forChannel(EMAIL).locale("bg").build()`. Replaces the inline
`notification(id, locale)` helper duplicated today in `NotificationResourceTest` and similar
ad hoc construction elsewhere.

**Custom assertions** — `NotificationAssert` (`assertThatNotification(n).isTerminal()
.hasStatus(FAILED).hasAttempts(3)`), and `ErrorResponseAssert`
(`assertThatError(response).hasStatus(409).hasCode("NOTIF_081").hasCategory("VALIDATION")`)
matching the **actual** `ErrorResponse` fields, not RFC 7807 field names (see Risks).

### Production seam required for adapter testability

None of the three senders currently accept a configurable provider host:
- `TelegramApiSender.TELEGRAM_API_BASE` is a hardcoded `private static final String`.
- `TwilioSmsSender.TWILIO_API_URL_TEMPLATE` is a hardcoded `private static final String`.
- `SendGridEmailSender` constructs `new SendGrid(apiKey)`, which defaults to `api.sendgrid.com`.

To point these at WireMock in tests, each gets one new config property with a default equal
to the current hardcoded value (no behavior change in prod):
- `TelegramConfig`: add `apiBaseUrl()` — `@WithName("api.base-url") @WithDefault("https://api.telegram.org/bot")`.
- `TwilioConfig`: add `apiBaseUrl()` — `@WithName("api.base-url") @WithDefault("https://api.twilio.com")`.
- `SendGridConfig`: add `Optional<String> apiBaseUrl()`; sender calls
  `request.setBaseUri(...)` when present (SendGrid Java SDK's `Request` supports a
  per-request base URI override — **to be confirmed during T9**, see Risks).

This is a deliberate, minimal, additive change to production config classes — not a
refactor of sender logic — needed because "mock-based unit tests on thin adapters are
theater" per the test-architecture skill; the alternative (mocking `Client`/`SendGrid`
directly) would assert only that a mock was called, not the real request shape.

### New test dependencies

- `org.wiremock:wiremock-standalone` (test scope) — not currently a dependency; standalone
  variant avoids Jetty/Vert.x classpath collisions with Quarkus.
- `org.assertj:assertj-core` (test scope) — not currently a dependency; needed for the
  custom assertion classes (`AbstractAssert` subclasses).

### Taxonomy applied per component

| Component | Level | Why |
|---|---|---|
| `TelegramApiSender`, `TwilioSmsSender`, `SendGridEmailSender` | INTEGRATION (WireMock) | Thin HTTP adapters — the behavior worth testing IS the request shape and error mapping. |
| `TemplateFileParser` | UNIT | Pure string parsing, no I/O, no framework. |
| `TemplateScanner` | UNIT | Filesystem walk over a test fixture directory; deterministic, no Quarkus context needed. |
| `TemplateLoader` | SLICE (`@QuarkusTest`) | Thin wrapper delegating to the injected Qute `Engine`. |
| `ChannelResource`, `MetricsResource`, `TemplateResource`, `WebhookResource` | API/CONTRACT (RestAssured) | Public HTTP contract: status codes, error bodies, validation. |

### Coverage measurement

`mvn verify` regenerates `target/jacoco-report/jacoco.xml`. Before-numbers are already
captured from the current build (see table below, pulled during investigation). After-numbers
are re-extracted from the same file post-implementation, per touched package.

| Package | Branch coverage today |
|---|---|
| `service.channel.telegram` | 0% |
| `service.channel.sms` | 0% |
| `service.channel.email` | 34.4% |
| `controller.resource` | 0% |
| `template.loading` | 0% |

## Risks & open questions

- **RFC 7807 mismatch**: the mission scope says "RFC 7807 error shape," but this codebase's
  actual error envelope (`ErrorResponse`: `code`/`title`/`message`/`category`/`timestamp`/`details`)
  is not RFC 7807 (no `type`, no `instance`, media type is plain JSON not
  `application/problem+json`). The kit's `ErrorResponseAssert` targets the real shape.
  Flagging in case a true RFC 7807 migration was actually intended — that would be a
  separate, larger spec, out of scope here.
- **SendGrid base-URI override unconfirmed**: needs verification that `com.sendgrid.Request`
  actually honors a per-request base URI override in the SDK version pinned in this project.
  If it doesn't, the fallback is to test `SendGridEmailSender` at a lower level (extract
  `buildMail`/`handleResponse` as already-separable pure logic units) and accept a smaller
  WireMock-covered surface for email than for Telegram/SMS. Resolved during T9; plan updated
  if the fallback is needed.
- **`testcontainers-bom` pinned at `2.0.2`** in `pom.xml` — unusually high for Testcontainers
  (normal releases are 1.x). Not touched by this spec; flagging in case it's a typo worth a
  separate fix.
- **Shared single WireMock instance**: relies on the three providers' path prefixes never
  colliding (`/bot*` for Telegram, `/2010-04-01/Accounts/*` for Twilio, `/mail/send` /
  configurable for SendGrid). If a future provider collides, split into per-class instances.
