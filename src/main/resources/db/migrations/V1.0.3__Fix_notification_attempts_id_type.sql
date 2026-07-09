-- V1.0.3__Fix_notification_attempts_id_type.sql
-- What: notification_attempts.id was declared UUID here (V1.0.0), but the
--       entity (NotificationAttempt extends PanacheEntity) has always used a
--       Long id (GenerationType.AUTO -> Postgres sequence "notification_attempts_seq"
--       on this dialect, allocationSize 50 - Hibernate's pooled sequence
--       optimizer). Flyway never actually applied V1.0.0-V1.0.2 in any real
--       environment until this mission fixed quarkus.flyway.locations (see
--       application.properties) - the real schema everywhere was built by
--       Hibernate's schema-management=update instead, which already made
--       this column bigint, backed by an auto-created "notification_attempts_seq"
--       (increment 50, no column-level DEFAULT - Hibernate calls nextval()
--       explicitly before each insert). Both empirically confirmed.
--
-- Why this migration still has to run everywhere: it's the first migration
--       Flyway actually executes for real. On a FRESH database (every
--       Testcontainers test run, or any brand-new environment) V1.0.0 just
--       ran for the first time moments ago and really did create a UUID
--       column - this migration converts that. On an EXISTING database
--       (baselined past V1.0.2 - see baseline-version in application.properties)
--       the column is already bigint with the sequence already in place -
--       every step below is conditional/idempotent so it's a safe no-op
--       there, INCLUDING the sequence's current value (see the advance-only
--       guard at the bottom - never regress a sequence that's already in
--       active use, since Hibernate's pooled allocator can legitimately be
--       ahead of MAX(id) at any time due to ordinary rollback gaps).
--
-- Data impact: notification_attempts is a pure, append-only audit trail -
--       never read back by id (NotificationStateService only ever appends),
--       never returned by any API (FailedNotificationResponse exposes only
--       NotificationRecord fields). No consumer depends on a specific id
--       VALUE, only that each row has some unique id. Where an old uuid
--       column exists (fresh-DB branch only), its values are discarded, not
--       mapped forward - there is nothing to preserve them for.
--
-- FK impact: none. The only foreign key on this table
--       (notification_id REFERENCES notifications(id)) points at a
--       different table's PK, untouched by this migration, in either
--       direction.
--
-- No window without a PK: this whole migration runs as one Flyway-managed
--       transaction; the old PK (if any) and the new PK are dropped/added
--       within it.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'notification_attempts' AND column_name = 'id' AND data_type = 'uuid'
    ) THEN
        ALTER TABLE notification_attempts DROP CONSTRAINT IF EXISTS notification_attempts_pkey;
        ALTER TABLE notification_attempts DROP COLUMN id;
        ALTER TABLE notification_attempts ADD COLUMN id BIGINT;
    END IF;
END $$;

-- INCREMENT BY 50 matches Hibernate's default @GeneratedValue allocationSize
-- (pooled sequence optimizer) - confirmed empirically against
-- schema-management.strategy=validate: "sequence [notification_attempts_seq]
-- defined inconsistent increment-size; found [1] but expecting [50]".
CREATE SEQUENCE IF NOT EXISTS notification_attempts_seq INCREMENT BY 50;

UPDATE notification_attempts SET id = nextval('notification_attempts_seq') WHERE id IS NULL;

ALTER TABLE notification_attempts ALTER COLUMN id SET NOT NULL;
ALTER TABLE notification_attempts ALTER COLUMN id SET DEFAULT nextval('notification_attempts_seq');
ALTER SEQUENCE notification_attempts_seq OWNED BY notification_attempts.id;

-- Advance-only: only raise the sequence if the table's real MAX(id) has
-- somehow gotten ahead of it (the fresh-DB branch above, converting
-- discarded-uuid rows onto a brand-new sequence starting at 1). Never lower
-- it - on an already-Hibernate-managed database the sequence's last_value
-- can legitimately sit ahead of MAX(id) (pooled allocation + ordinary
-- rollback gaps: nextval() is not transactional, so a rolled-back insert
-- still burns its reserved id). Confirmed empirically with a reconciliation
-- test that seeds a sequence ahead of MAX(id) and asserts it never drops.
DO $$
DECLARE
    current_max BIGINT;
    seq_last BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) INTO current_max FROM notification_attempts;
    SELECT last_value INTO seq_last FROM notification_attempts_seq;
    IF current_max > seq_last THEN
        PERFORM setval('notification_attempts_seq', current_max);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'notification_attempts_pkey'
    ) THEN
        ALTER TABLE notification_attempts ADD CONSTRAINT notification_attempts_pkey PRIMARY KEY (id);
    END IF;
END $$;
