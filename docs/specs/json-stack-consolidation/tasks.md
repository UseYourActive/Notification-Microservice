# Tasks: json-stack-consolidation

- [x] T1: Capture pre-swap golden response baseline — files:
  `docs/specs/json-stack-consolidation/golden/before/*.txt` (new) — done
  when: with the app running locally against current `master`-equivalent
  code (dual JSON-B/Jackson still on the classpath, via
  `scripts/deploy-dev.ps1` / `docker-compose up`), `curl -i` output (status
  line + headers + body, unmodified) is saved for each of: `POST
  /api/v1/notifications/send` success (EMAIL channel, no template — the
  same safe pattern `SendNotificationLiveTest` uses, captured by Mailpit,
  no real delivery), `POST /api/v1/notifications/send` validation-400
  (missing `recipient`), `POST /api/v1/notifications/send` invalid-enum
  400 (`CARRIER_PIGEON`), `GET /api/v1/notifications/failed`, `GET
  /api/v1/channels`, `GET /api/v1/metrics/today`, and `GET
  /api/v1/templates/discovery` (file-based, no DB template needs seeding
  first — the cheap read). Plus `GET /q/openapi` — captured here
  specifically because this is the only moment a genuine pre-T2 copy can
  exist for T5 to diff against; recapturing it later would require a
  checkout-and-redeploy detour. Eight files land under `golden/before/`,
  committed as-is (this task makes no production code change — it is
  evidence, not a fix).

- [x] T2: Remove `quarkus-rest-jsonb`, retire the dormant JSON-B mapper,
  Jackson becomes the sole reader — files: `pom.xml` (delete the
  `quarkus-rest-jsonb` dependency block, `pom.xml:72-75`),
  `src/main/java/bg/sit_varna/sit/si/exception/mapper/JsonbDeserializationExceptionMapper.java`
  (delete — it implements `ExceptionMapper<JsonbException>`, and
  `jakarta.json.bind.JsonbException` stops being on the classpath the
  moment the extension is removed, so mapper and extension must leave in
  the same commit or the build won't compile),
  `src/test/java/bg/sit_varna/sit/si/api/NotificationResourceTest.java`
  (line 65 — `.hasDetailContaining("NotificationChannel")` asserted the
  JSON-B-path detail shape; update to assert the Jackson-path shape, the
  literal field name `"channel"`, since this in-process `@QuarkusTest` now
  exercises the real Jackson mapper) — done when: `mvn dependency:tree`
  shows no `jakarta.json.bind`/Yasson artifact anywhere in the tree
  (ruling out a transitive reintroduction, e.g. via the SendGrid SDK, per
  the plan's risk note), `mvn test` (the full 166-test internal suite,
  `live` excluded as usual) is green, and no source file outside this
  list changed.

- [x] T3: Capture post-swap golden responses and diff against the T1
  baseline — files: `docs/specs/json-stack-consolidation/golden/after/*.txt`
  (new), `docs/specs/json-stack-consolidation/golden/diff-report.md` (new)
  — done when: the app is redeployed (Jackson-only build) and the same
  eight `curl -i` captures from T1 are repeated with identical requests
  (the `/q/openapi` recapture here is a convenience copy for T5, not this
  task's concern). The diff tooling compares **parsed/canonicalized JSON**
  (not raw bytes) and `diff-report.md` records, per endpoint, one of three
  verdicts:
  - **(a) semantic difference** — a field missing/extra, a changed value
    or format, a null that appeared or disappeared. Stop-and-fix: restore
    the old wire shape via Jackson config (e.g. `@JsonInclude`) before this
    task can close. Not a documented-and-ignored discrepancy.
  - **(b) field-order-only difference** — acceptable as-is, since Yasson
    serializes properties alphabetically and Jackson uses declaration
    order, so a JSON-B-written endpoint reordering under Jackson is exactly
    the signal that JSON-B was silently the writer there. Documented in
    `diff-report.md` with the rationale (JSON object members are
    unordered; no sane consumer depends on order) — do **not** add
    property-order annotations to chase byte-identity.
  - **(c) named volatile field** — normalized before comparison, not
    diffed: `notificationId` (fresh UUID per send-success), any
    timestamp/`createdAt`/`updatedAt` value tied to capture time, and the
    `Date` response header. Listed explicitly so "volatile" isn't a
    loophole for real drift.
  Every one of the eight captures gets an explicit verdict in the report;
  none may be silently omitted.

  **Mid-task finding (reality contradicted the "no change expected"
  assumption in plan.md's blast-radius table, per spec-driven-dev's
  "stop, flag, continue" rule):** the first post-swap capture of
  `GET /notifications/failed` showed `FailedNotificationResponse` gaining
  an explicit `"templateName": null` that JSON-B had been silently
  omitting (JSON-B's default excludes nulls; the DTO had no
  `@JsonInclude`, unlike `ErrorResponse`, which does). This is a genuine
  (a) semantic difference — fixed in the same commit by adding
  `@JsonInclude(JsonInclude.Include.NON_NULL)` to
  `src/main/java/bg/sit_varna/sit/si/dto/response/FailedNotificationResponse.java`,
  then re-captured and re-verified as (b) field-order-only. Full details
  in `diff-report.md`. No other DTO showed a tier-(a) difference.

- [x] T4: Prophecy check — the field-name asymmetry improves, live, and an
  assertion proves it — files:
  `src/test/java/bg/sit_varna/sit/si/qacommons/InvalidChannelValidationLiveTest.java`
  (line 54 — replace `assertThat(error.message()).contains("NotificationChannel")`
  with an assertion on the literal JSON field name, `"channel"`, now
  available via `InvalidFormatException.getPath()` on the Jackson path;
  update the class comment referencing the old JSON-B-path behavior) —
  done when: against the T3-redeployed (Jackson-only) service, `mvn test
  -DrunLive=true` runs the full live black-box suite and all 8 pass,
  including the strengthened assertion in this file, confirming live (not
  just by reading the mapper code) that the documented message asymmetry
  from `api-findings-fixes/plan.md` resolved in the predicted direction.

- [x] T5: Manual `/q/openapi` diff and real-response spot check — files:
  none (verification-only task; findings recorded in the commit message,
  not a new doc — the golden diff in T3 is the durable record) — done
  when: `/q/openapi` fetched from the Jackson-only running service is
  diffed against `golden/before/openapi.txt` (captured in T1, the only
  point a genuine pre-swap copy could exist) — schemas should be identical
  (no DTO or annotation changed, only which provider introspects them,
  which is not itself part of the OpenAPI contract) — with any difference
  investigated and explained before proceeding, and a live spot check
  (browser or curl) on `send`, `channels`, `failed-list`, and `metrics`
  confirms responses read as expected — this is the mission's required
  manual gate, distinct from and in addition to the automated diff in T3.

- [x] T6 (best-effort, non-blocking): Native image size before/after —
  files: none (observation, not a code change; recorded in the commit
  message for this task, or explicitly skipped with a one-line reason if
  it would meaningfully slow the mission) — done when: either two local
  `mvn package -Pnative -Dquarkus.native.container-build=true
  -Dquarkus.container-image.build=false` builds are run (one on the
  pre-T2 commit, one post-T2) and the resulting runner binary sizes are
  compared and reported, or — if skipped — the commit/PR notes say so
  explicitly rather than silently omitting it. The actual native-image CI
  gate (`.github/workflows/build.yml`'s `native-build-test`, master-only,
  post-merge) is unaffected either way and is not what this task verifies.

  **Result:** before (T1, dual-stack): 131,199,272 bytes (125.12 MiB).
  After (T2+, Jackson-only): 129,732,904 bytes (123.72 MiB). Delta:
  1,466,368 bytes (1.40 MiB), 1.12% smaller. The pre-swap build log also
  shows a Yasson-contributed native-image config
  (`org.eclipse.yasson-3.0.4.jar`'s `native-image.properties`,
  `-H:IncludeResourceBundles`) absent post-swap, and fewer reachable
  types/fields/methods in the analysis phase (30,403→29,551 types,
  42,144→41,663 fields, 144,464→142,506 methods) - consistent with one
  fewer JSON stack being analyzed, not just a rounding artifact.

- [x] T7: Close out — resolve finding #4 — files:
  `docs/specs/qa-commons-live-suite/findings.md` (finding #4 section:
  change status to **RESOLVED**, add a "Fix" paragraph naming the removed
  extension and mapper, and a "Commits" line with the T1-T6 commit SHAs,
  matching the format of findings #1-#3 in the same file) — done when:
  finding #4 reads as resolved with real commit references (written after
  those commits exist, so this genuinely is the last task), and every
  checkbox above this one is `[x]`.
