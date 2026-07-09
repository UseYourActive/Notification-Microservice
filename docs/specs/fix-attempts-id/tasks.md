# Tasks: fix-attempts-id

- [x] T1: End dual schema ownership and fix `notification_attempts.id`.
      `application.properties`: `quarkus.flyway.locations=classpath:db/migrations`,
      `quarkus.flyway.baseline-on-migrate=true` (already set),
      `quarkus.flyway.baseline-version=1.0.2`,
      `quarkus.hibernate-orm.schema-management.strategy=validate` (was
      `update`). Add `V1.0.3__Fix_notification_attempts_id_type.sql` per
      plan.md Design §2 (conditional on current column type, sequence
      `INCREMENT BY 50`, advance-only `setval`, no window without a PK,
      idempotent-safe re-run).
      Tests (same commit):
      - `NotificationAttemptIdMigrationTest` (`@QuarkusTest`, fresh
        Testcontainers DB): all migrations up to `V1.0.3` apply for real
        (`flyway_schema_history`), `notification_attempts.id` is `bigint`
        `NOT NULL`, inserting attempts generates distinct non-null `Long`
        ids.
      - `NotificationAttemptsMigrationReconciliationTest` (plain JUnit,
        drives `Flyway` directly against a hand-seeded "already
        Hibernate-built" schema — bigint id, `notification_attempts_seq`
        pre-advanced ahead of `MAX(id)` to reproduce a real pooled-allocation
        gap): confirms `V1.0.3` is a safe no-op and never regresses the
        sequence.
      - Full suite re-run with `schema-management.strategy=validate` on,
        confirming no other drift anywhere in the app.
      Files: `application.properties`,
      `src/main/resources/db/migrations/V1.0.3__Fix_notification_attempts_id_type.sql`,
      `src/test/java/bg/sit_varna/sit/si/migration/NotificationAttemptIdMigrationTest.java`,
      `src/test/java/bg/sit_varna/sit/si/migration/NotificationAttemptsMigrationReconciliationTest.java`,
      `pom.xml` (added `flyway-database-postgresql`, test-scoped — needed by
      the reconciliation test, which drives Flyway without a Quarkus boot).
      Done when: `mvn test` green with `validate` mode on.

      Note on process: the first draft of `V1.0.3` had two real bugs, both
      caught by these tests rather than assumed correct — a wrong sequence
      increment (default 1, needed 50) and an unconditional `setval()` that
      regressed an already-in-use sequence backward. Forward-fixing the
      second one turned out to be impossible (the pre-regression sequence
      value is unrecoverable once overwritten), so — with your explicit
      approval, since nothing had been committed or applied anywhere outside
      this session's own test runs — the flawed drafts were replaced with one
      correct `V1.0.3` instead of layered forward-fixes.

- [x] T2: Replace the check-then-act dedup guard in
      `NotificationService.persistRecord()` with unique-constraint-violation
      handling per plan.md Design §3: remove the `findById` pre-check,
      `persist()` + explicit `flush()`, catch `PersistenceException`, unwrap
      the cause chain for `org.hibernate.exception.ConstraintViolationException`,
      match on constraint name (`notifications_pkey`) or SQLState `23505`
      (not any unique-violation anywhere), rethrow anything else unchanged,
      throw new `DuplicateNotificationException` on a real match. Add
      `NotificationErrorCode.DUPLICATE_NOTIFICATION` (409),
      `ErrorCategory.CONFLICT`, `DuplicateNotificationException`, and the
      matching i18n keys in `messages_en.yaml`/`messages_bg.yaml`.
      Test: persisting the same id twice (two `REQUIRES_NEW` calls) throws
      `DuplicateNotificationException` on the second and asserts the actual
      Hibernate exception shape (don't assume bare vs. wrapped — assert
      against what's really thrown); the first record is unaffected; an
      unrelated `PersistenceException` is rethrown, not swallowed as a false
      "duplicate"; existing `NotificationProcessorDedupTest` and related
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
