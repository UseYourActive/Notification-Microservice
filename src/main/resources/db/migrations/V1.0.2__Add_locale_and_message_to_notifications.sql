-- V1.0.2__Add_locale_and_message_to_notifications.sql
-- What: adds locale/message to notifications.
-- Why: durable-notification-queue mission — the poller reconstructs the full
--      Notification DTO from this row on every claim (not just crash recovery),
--      so any field needed to reprocess a notification must be persisted here.
--      Previously locale and the raw (non-template) message text were only ever
--      held in the in-memory Emitter payload and never wrote to this table,
--      which silently loses that data now that Postgres is the queue of record.
-- Data impact: none. Both columns are nullable, safe on a non-empty table.

ALTER TABLE notifications
    ADD COLUMN locale VARCHAR(50),
    ADD COLUMN message TEXT;
