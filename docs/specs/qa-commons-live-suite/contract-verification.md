# Contract verification — qa-commons-live-suite (T3)

Verified against the running compose stack (`docker-compose up`,
`localhost:8080`) on 2026-07-15, both via `/q/openapi` and direct `curl`
against the live endpoints, before locking any endpoint class.

## `GET /api/v1/channels`

Matches `GetChannelsResponse` exactly. OpenAPI's generated `total` example
shows `4`, but the actual live response is `total: 3` — a stale/generic
SmallRye example, not real drift; confirmed via direct call
(`channels[].{name,description,enabled}`, `total`).

## `POST /api/v1/notifications/send`

Matches `SendNotificationRequest`/`SendNotificationResponse` field-for-field.
**Drift found:** the OpenAPI spec documents `200` as the success response
code (`@APIResponse` annotation on `NotificationApi`), but the running
service actually returns **202 Accepted** — matches
`NotificationResource.java:74` and this plan's design (which already
assumed 202). This is stale OpenAPI documentation in production code, not a
behavior bug, and out of scope to touch in this branch. Live response body
confirmed: `notificationId`, `status` (`"QUEUED"` observed), `message`,
`timestamp`, `recipient`, `channel`.

**Validation negative confirmed live:** blank `recipient` → `400`, body
`{"code":"VALIDATION_FAILED","details":[...],"message":...,"timestamp":...}`.
`title`/`category` are absent (`ErrorResponse` is `@JsonInclude(NON_NULL)`
and the mapper leaves them null) — `expectFailure()` assertions should check
`code`/`details`/`message`, not assume `title`/`category` are populated.

**Known bug confirmed live:** `channel: "CARRIER_PIGEON"` → `500`, plain-text
Quarkus error page (not JSON) — confirms `ApiResult` classifies as
`Unparsed(status=500, ...)`, exactly as designed for
`InvalidChannelFindingLiveTest`.

## `GET /api/v1/notifications/failed`

Matches `PageResponse<FailedNotificationResponse>` exactly:
`items[].{notificationId,recipient,channel,status,createdAt,updatedAt}`,
`page`, `size`, `totalItems`, `totalPages`. OpenAPI itself can't resolve the
generic item type either (`items: {type: array, items: {}}` — no `$ref`),
independently confirming the generics-erasure problem plan.md's
`FailedNotificationsPageResponse` shim exists to solve. Live data present
(177 failed rows in this environment) — confirms the "tolerating empty"
requirement is about the *test*, not this environment.

## `GET /api/v1/metrics/today`

Matches the raw-`Map` design: live body is
`{"total":0,"successRate":0.0,"byChannel":{"SMS":0,"EMAIL":0,"TELEGRAM":0}}`.
OpenAPI schema is a bare `{"type": "object"}` with no properties — SmallRye
can't infer anything from `Map<String,Object>`, independently confirming
finding #3 (no typed production DTO).

## Conclusion

No plan changes required. All four endpoint shapes and both behavioral
assumptions (validation-negative 400, invalid-channel 500) verified live
before writing any test against them.
