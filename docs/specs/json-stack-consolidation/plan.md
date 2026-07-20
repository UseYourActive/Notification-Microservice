# json-stack-consolidation

## Problem

This service declares both `quarkus-rest-jsonb` (`pom.xml:74`) and
`quarkus-rest-jackson` (`pom.xml:82`) as direct dependencies. RESTEasy
Reactive's `MessageBodyReader`/`MessageBodyWriter` selection between two
competing JSON providers is not a documented contract — it is priority/
registration-order behavior that can shift across Quarkus/RESTEasy Reactive
versions, and today it is not even uniform across this app's own endpoints
(confirmed below). `docs/specs/qa-commons-live-suite/findings.md` #2 records
this as open: "which reader wins is an accident of classpath/registration
order, not a design decision." Carrying two full JSON stacks also adds
native-image weight for no behavioral benefit, since only one stack's
behavior is ever intentionally exercised.

## Investigation (the evidence, not the assumption)

**Why both extensions are present.** Both `quarkus-rest-jsonb` and
`quarkus-rest-jackson` have been in `pom.xml` since `5fb5d2c` ("Initial
commit", 2025-11-11), added together with no commit message or code comment
explaining the coexistence. Nothing in the subsequent 20 commits touching
`pom.xml` references JSON-B intentionally. This has every signature of a
`code.quarkus.io` scaffold artifact — both "REST JSON-B" and "REST Jackson"
extensions selected when generating the project — not a deliberate
two-stack design.

**Which reader wins, and where.** Confirmed live (captured during
`api-findings-fixes`, see `docs/specs/api-findings-fixes/plan.md`):
`POST /api/v1/notifications/send` throws `jakarta.json.bind.JsonbException`
on a malformed enum, meaning JSON-B (Yasson) — not Jackson — is the active
`MessageBodyReader` for that endpoint's request body today. This is the
concrete evidence the mission brief refers to; the race is not merely
theoretical.

**What actually depends on JSON-B semantics or annotations: nothing,
in production code.** A full grep of `src/main/java` for
`jakarta.json.bind`, `JsonbBuilder`, `JsonbConfig`, and any `@Jsonb*`
annotation returns zero hits outside
`JsonbDeserializationExceptionMapper` — a mapper added by the *previous*
mission specifically to catch JSON-B's exception type defensively (it
targets the exception JSON-B *throws*, not a producer that relies on JSON-B
behavior). No DTO carries a `@Jsonb*` annotation. No `JsonbConfig` /
`JsonbConfigCustomizer` bean exists. Nothing pulled `quarkus-rest-jsonb` in
transitively — it is a direct, first-party `pom.xml` entry.

**What actually depends on Jackson, pervasively:**
- `ErrorResponse` — `@JsonInclude(JsonInclude.Include.NON_NULL)`
- `SendGridEvent` (webhook DTO) — `@JsonIgnoreProperties(ignoreUnknown = true)`, `@JsonProperty("sg_message_id")`
- `Notification` (internal domain model) — `@JsonDeserialize(builder = Notification.Builder.class)` + `@JsonPOJOBuilder(withPrefix = "")`, a Jackson-only builder-deserialization mechanism. Not itself a REST wire type (built via `NotificationMapper.toDomain()`, not deserialized off `POST /send`'s body), but proof Jackson is this codebase's assumed JSON tool wherever custom (de)serialization behavior is needed.
- `WebhookService` — CDI-injected `ObjectMapper` (the bean `quarkus-rest-jackson`/`quarkus-jackson` registers), used to parse SendGrid webhook payloads via `TypeReference`
- `TelegramApiSender` — a manually-constructed `ObjectMapper` for parsing Telegram Bot API responses
- `jackson-dataformat-yaml` — direct dependency, used for config/resource-bundle reading (`YamlResourceBundleControl`)
- `quarkus-rest-client-jackson` — direct dependency, used for the outbound REST clients

**Qute/mail path:** `quarkus-qute` and `quarkus-mailer` do not serialize
through either JSON stack — Qute renders text/HTML templates
(mustache-style substitution into `.html`/`.txt` files), not JSON, and the
mailer sends the rendered output as an email body. Neither is implicated in
this consolidation.

**Conclusion the evidence supports:** Jackson is this codebase's real,
consistently-used JSON stack; JSON-B's presence is unexplained scaffold
residue with a single accidental foothold (winning the reader race for one
endpoint's request parsing). This earns the Jackson recommendation rather
than assuming it.

## Goal / Non-goals

**Goals**
- Exactly one JSON provider on the classpath: Jackson
  (`quarkus-rest-jackson`). Remove `quarkus-rest-jsonb`.
- Zero wire-format change on any endpoint, verified by the existing
  166-test internal suite, the 8/8 live black-box suite (whose
  contract-shape tests exist precisely to catch this), and a manual
  `/q/openapi` + real-response spot check.
- `JacksonDeserializationExceptionMapper` (dormant since
  `api-findings-fixes`) becomes the live path for malformed request bodies;
  the invalid-`channel` 400 path is re-verified live, and the error detail
  is confirmed to now include the literal JSON field name (`"channel"`)
  instead of the enum type name — the documented message-asymmetry
  improving, as predicted in that mission's plan.
- `JsonbDeserializationExceptionMapper` removed once nothing in the suite
  throws `JsonbException` anymore (it becomes unreachable dead code once
  the JSON-B reader/writer is gone).
- `findings.md` (`docs/specs/qa-commons-live-suite/findings.md`) #4 updated
  to **RESOLVED** with commit references, matching the format of #1-#3.
- Note the native-image size delta (before/after) if it can be captured
  without materially slowing this mission down — see Risks.

**Non-goals**
- No behavior change to any response shape, field name, null-inclusion
  policy, date/time format, or enum representation. This is a plumbing
  swap; the wire contract is frozen.
- No new validation, no new error codes, no endpoint redesign.
- No change to `application.yml`/`quarkus.jackson.*` config beyond what's
  strictly required to keep output byte-for-byte equivalent (none is
  currently expected — see Design).
- No touching of Qute, mailer, Redis, or the outbound REST clients beyond
  what's needed if they turn out to depend on the JSON-B extension being
  present (evidence above says they don't).
- Not attempting to fix the field-name asymmetry by adding custom Yasson
  config — the asymmetry disappears because JSON-B is gone, not because
  it's patched.

## Design

**Change:** delete the `quarkus-rest-jsonb` dependency block from
`pom.xml` (currently lines 72-75). No replacement needed — Jackson already
covers every JSON responsibility in this app per the investigation above.

**Why Jackson and not JSON-B, restated as a decision:** JSON-B has zero
intentional production usage (see Investigation); Jackson has the DTO
annotations, the CDI-managed `ObjectMapper`, the YAML dependency, and the
outbound REST client extension. Moving to JSON-B would mean adding
`@JsonbNillable`-equivalents, replacing `@JsonDeserialize(builder=...)`
with a JSON-B builder mechanism, and losing `jackson-dataformat-yaml`
consistency — strictly more work for a stack nothing currently needs.
Rejected outright, not seriously considered as an alternative.

**Blast radius — every response/request type re-verified, listed
honestly (not assumed safe):**

| Type | Risk surface | Expected outcome |
|---|---|---|
| `SendNotificationRequest` (record, `NotificationChannel` enum, `Map<String,Object> data`) | Request body — today's confirmed JSON-B reader. Record-component binding, enum parsing, arbitrary nested `Map<String,Object>` (template data) all move to Jackson's record support. | No change — Jackson supports record deserialization natively (Quarkus registers `ParameterNamesModule`/JDK record support); `Map<String,Object>` nested JSON already round-trips through Jackson elsewhere (Telegram, webhook paths). |
| `CreateTemplateRequest`, `UpdateTemplateRequest` (records, plain `String`/`boolean`) | Request body, same class of risk as above, unconfirmed which reader currently serves these (the race is not uniform per the mission brief) — must be verified live, not assumed same as `/send`. | No change expected; simplest possible record shapes. |
| `TemplateValidationRequest`, `GetTemplatesRequest`, `GetFailedNotificationsRequest` | `@BeanParam` query-param binding, not JSON body — **not in scope for the reader race** at all. | No change; listed for completeness. |
| `FailedNotificationResponse`, `TemplateResponse` (`LocalDateTime` fields) | Date/time formatting differs by library default (Jackson needs `jackson-datatype-jsr310`, already active via `quarkus-rest-jackson`'s auto-registered `JavaTimeModule`; JSON-B has built-in `java.time` ISO-8601 support with possibly different precision/offset rules). Since Jackson already appears to own response *writing* today (see below), this should be a no-op, but gets an explicit before/after diff. | No change expected — spot-checked live. |
| `TemplateResponse.id` (`UUID`) | Library-specific `UUID` (de)serialization quirks (rare but real: e.g. handling of unusual whitespace/case). | No change expected — spot-checked live. |
| `PageResponse<T>` (generic) | Already the subject of `findings.md` #2 — generic type erasure was a *test-framework* problem, not a production one; production writing already goes through Jackson (Quarkus's generic-aware `ObjectMapper`, unlike raw JSON-B which has its own generic handling). | No change expected. |
| `MetricsResponse` (`Map<String, Long>`) | Simple map, low risk. | No change expected. |
| `ErrorResponse` (`@JsonInclude(NON_NULL)`) | JSON-B's default null-handling *excludes* nulls without needing an annotation; Jackson needs the annotation, which is already present. If JSON-B were ever writing this type, removing it changes nothing (Jackson already excludes nulls here explicitly). | No change expected — this is the type most likely to have silently differed if JSON-B were writing it, so gets a specific null-field spot check. |
| `NotificationChannel`, `NotificationStatus` (enums, no `@JsonValue`/`@Jsonb*`) | Both libraries default to `Enum.name()`; no casing transform configured on either side. | No change expected. |
| `GetChannelsResponse`, `GetLocalesResponse`, `GetTemplatesResponse`, `TemplateValidationResponse`, `SendNotificationResponse` | Plain records/strings/lists, lowest risk in the set. | No change expected. |

**Reader-race non-uniformity is itself a verification target.** Because
the mission brief and the live evidence only pin JSON-B as the winner for
`POST /send`, Phase 2 must not assume `createTemplate`/`updateTemplate`
were also JSON-B-served — task T3 verifies actual pre-change behavior for
those endpoints too (best-effort, since post-removal there's only one
reader left to test against, so this is a "confirm no exception mapper
plumbing was silently depending on JSON-B for these" check, not a full
before/after diff).

**Deserialization mapper swap.** `JacksonDeserializationExceptionMapper`
was written and unit-tested in `api-findings-fixes` specifically to be
ready for this moment (`ExceptionMapper<InvalidFormatException>`). Once
JSON-B is gone, it is the only path malformed-enum bodies can take;
`JsonbDeserializationExceptionMapper` becomes unreachable. It is deleted
in the last task, gated on confirming (live, not just by inspection) that
nothing still throws `JsonbException` — i.e. the extension removal
actually took effect and there's no second Yasson entrypoint hiding
somewhere (there is no evidence of one, but the gate is empirical, not
assumed).

**Native image weight.** `native-build-test` in
`.github/workflows/build.yml` runs `mvn verify -Pnative
-Dquarkus.native.container-build=true` on `master` only (post-merge, not
per-PR) — this mission's PR will not itself trigger it. Docker is
available locally, so a before/after native image size comparison is
feasible via the same container-build flag, but each build is a genuine
~10-minute operation per the workflow's own comment; this is captured as a
best-effort task (T6), not a blocking gate — the actual CI gate is the
existing post-merge `native-build-test` job staying green, which this
mission does not change the meaning of.

## Risks & open questions

- **Non-uniform reader race**: confirmed JSON-B wins for `/send`, not
  confirmed (yet) for `createTemplate`/`updateTemplate`. Resolved by
  explicit pre-removal live checks in task T3, not assumed.
- **Native image size before/after** requires two ~10-minute local Docker
  builds; if this materially slows the mission, the size delta is reported
  as "not captured, CI native-build-test is the authoritative gate" rather
  than blocking task completion on it. Flagging now so it isn't a silent
  scope cut later.
- **`JsonbDeserializationExceptionMapper` removal** is gated on an empirical
  check (live suite green, manual spot check throws no `JsonbException`),
  not a static "we removed the dependency so it must be dead" assumption —
  Yasson could in principle still be reachable through the SendGrid SDK's
  own dependency tree (unrelated to `quarkus-rest-jsonb` but worth ruling
  out); dependency:tree diff in T1 checks for this.
- **`GetFailedNotificationsRequest`/`GetTemplatesRequest`/
  `TemplateValidationRequest`** are `@BeanParam` query-param types, entirely
  outside the JSON reader race — included in the blast-radius table only
  for completeness/honesty, not because they're actually at risk.
