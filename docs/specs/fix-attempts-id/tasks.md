# Tasks: fix-attempts-id

- [ ] T1: Fix Flyway migration discovery — `application.properties`:
      `quarkus.flyway.locations=classpath:db/migrations`,
      `quarkus.flyway.baseline-version=1.0.2`. Test: a `@QuarkusTest` against
      the Testcontainers Postgres asserting `flyway_schema_history` has
      exactly 3 applied rows (`1.0.0`, `1.0.1`, `1.0.2`) after a fresh boot —
      proves migrations are now actually discovered and applied, not just
      that the app still boots.
      Files: `src/main/resources/application.properties`, new test.
      Done when: the test passes and shows real migration application on a
      fresh DB (this was previously impossible — Flyway silently found zero
      migrations).

- [ ] T2: Add `V1.0.3__Fix_notification_attempts_id_type.sql` per plan.md
      Design §2 (conditional on current column type, sequence-backed BIGINT,
      no window without a PK, idempotent-safe re-run). Test (same class as
      T1 or new): boot fresh, insert two `NotificationAttempt` rows via
      `NotificationStateService`, assert both get non-null, distinct `Long`
      ids with no manual id assignment; assert `notification_attempts.id` is
      `bigint` via `information_schema`. Add a second test seeding the
      table in the shape Hibernate's auto-DDL already produces in real
      environments (bigint column + `notification_attempts_seq`, no old
      `uuid` column) and confirming the migration is a safe no-op against it.
      Files: `src/main/resources/db/migrations/V1.0.3__...sql`, test class
      (e.g. `NotificationAttemptIdMigrationTest`).
      Done when: both scenarios pass; `mvn test` green.

- [ ] T3: Replace the check-then-act dedup guard in
      `NotificationService.persistRecord()` with unique-constraint-violation
      handling per plan.md Design §3: remove the `findById` pre-check,
      `persist()` + explicit `flush()`, catch
      `org.hibernate.exception.ConstraintViolationException`, throw new
      `DuplicateNotificationException`. Add
      `NotificationErrorCode.DUPLICATE_NOTIFICATION` (409),
      `ErrorCategory.CONFLICT`, `DuplicateNotificationException`, and the
      matching i18n keys in `messages_en.yaml`/`messages_bg.yaml`. Test:
      persisting the same id twice (two `REQUIRES_NEW` calls) throws
      `DuplicateNotificationException` on the second, and the first record
      is unaffected; existing `NotificationProcessorDedupTest` and related
      suites stay green.
      Files: `NotificationService.java`, `NotificationErrorCode.java`,
      `ErrorCategory.java`, `exception/exceptions/DuplicateNotificationException.java`
      (new), `messages_en.yaml`, `messages_bg.yaml`, test.
      Done when: new test passes; full suite green.

## After all tasks

- Run the full test suite once more.
- code-reviewer pass (java-engineering:code-reviewer) on the full diff
  against `feature/durable-queue`.
- Push `feature/fix-attempts-id`, report ready for PR review (base:
  `feature/durable-queue`, not `master`).
