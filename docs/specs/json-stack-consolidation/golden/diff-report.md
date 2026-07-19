# Golden response diff — pre-swap vs. post-swap (T3)

Compares parsed/canonicalized JSON (not raw bytes) between
`golden/before/` (dual JSON-B/Jackson, captured T1) and `golden/after/`
(Jackson-only, captured post-T2, with one fix folded in — see below)
for the same eight requests. Three-tier verdict per endpoint:

- **(a) semantic difference** — missing/extra field, changed value or
  format, a null appearing/disappearing. Stop-and-fix.
- **(b) field-order-only difference** — acceptable. JSON object members
  are unordered per the JSON spec; no sane consumer depends on order.
  Documented, not chased with property-order annotations.
- **(c) named volatile/expected difference** — normalized or explained,
  not a stack-behavior signal.

## Headline finding: JSON-B was silently the writer, not just the reader

Before the swap, `POST /send`'s response body was
`{"channel":...,"message":...,"notificationId":...,"recipient":...,"status":...,"timestamp":...}`
— alphabetical. `SendNotificationResponse`'s declared field order is
`notificationId, status, message, timestamp, recipient, channel`. Yasson
(JSON-B) serializes properties alphabetically by default; Jackson uses
declaration order. After the swap, the body reorders to match the record's
declaration order exactly. This pattern repeats across every endpoint
captured. **Conclusion: JSON-B was the active `MessageBodyWriter` for
these responses too, not only the reader for `POST /send` as originally
evidenced in `api-findings-fixes`.** This does not change the
recommendation (Jackson evidence in `plan.md` stands regardless of which
provider happened to be writing what) — it changes what "wire freeze"
actually had to be verified against, which is exactly why this diff task
exists instead of trusting the live suite's shape-only assertions.

## Per-endpoint verdicts

| Endpoint | Verdict | Notes |
|---|---|---|
| `POST /send` success | **(c)** | Body fields identical (`channel`, `message`, `notificationId`, `recipient`, `status`, `timestamp`). Only `recipient`, `notificationId`, `timestamp` differ — `notificationId`/`timestamp` are inherently per-request; `recipient` differs only because the two captures intentionally used a fresh `json-stack-golden-<epoch>@example.com` address each time (good practice for a real outbound send), not because of any serialization behavior. Field order changed alphabetical → declaration order (tier b, noted for completeness, not a separate verdict). |
| `POST /send` validation-400 (missing recipient) | **(b)** | `ErrorResponse` body: `code`, `message`, `timestamp`, `details` all identical in content; order changes from `code, details, message, timestamp` (alphabetical) to `code, message, timestamp, details` (declaration order, `title`/`category` still omitted as null in both — `@JsonInclude(NON_NULL)` was already present here). |
| `POST /send` invalid-enum 400 | **(c)**, expected content change | `message`/`details` text changes from `"...for NotificationChannel; accepted values..."` (enum type name) to `"...for field 'channel'; accepted values..."` (literal JSON field name). This is the documented, *predicted* message-asymmetry improvement from `api-findings-fixes/plan.md` — not a regression. Formally asserted live in T4, not just observed here. |
| `GET /notifications/failed` | **(a) found and fixed**, then (b)+(c) | Initial post-T2 capture showed `FailedNotificationResponse` gaining an explicit `"templateName": null` that JSON-B had been silently omitting (JSON-B's default excludes nulls; the DTO had no `@JsonInclude`, unlike `ErrorResponse`). **Fixed**: added `@JsonInclude(JsonInclude.Include.NON_NULL)` to `FailedNotificationResponse` (matching the existing pattern on `ErrorResponse`), restoring null-omission. Re-captured after the fix: item key-sets identical (`channel`, `createdAt`, `notificationId`, `recipient`, `status`, `updatedAt`) in both before/after, only field order differs (tier b). `totalItems` (215 → 217) and list contents differ only because the live dev DB accrued more failed rows between the two captures (this mission's own golden-capture sends and background async delivery attempts) — a timing/data artifact of testing against a stateful live instance, not a serialization concern (tier c). |
| `GET /channels` | **(b)** | Identical content. `ChannelInfo` field order: `description, enabled, name` (alphabetical) → `name, description, enabled` (declaration order in `ChannelResource`). |
| `GET /metrics/today` | **(c)** | Identical structure and keys (`byChannel`, `successRate`, `total`). Values (`total`/`byChannel.EMAIL`: 75 → 76) differ only because one more notification was processed between the two live captures (the mission's own test sends) — a live-data timing artifact, not a stack behavior difference. |
| `GET /templates/discovery` | **(b)** | Identical content. `TemplateInfo` field order: `description, locales, name, type` (alphabetical) → `name, type, locales, description` (declaration order). |
| `GET /q/openapi` | **identical** | Byte-for-byte identical (`diff` empty, both 1387 lines). Confirms the OpenAPI schema is generated from Jandex/annotation introspection independent of which JSON runtime is active — exactly as predicted in `plan.md`. |

## Verdict

One real (a) found and fixed (`FailedNotificationResponse` null-handling).
Every other endpoint is (b) field-order-only or (c) volatile/expected. No
outstanding semantic difference. Wire contract is frozen, with one DTO
corrected to actually honor that freeze.
