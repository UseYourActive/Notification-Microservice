-- V1.0.1__Add_queue_claim_columns.sql
-- What: adds locked_by/locked_at/attempts_count to notifications, plus a
--       composite (status, created_at) index to support the poller's claim query
--       (SELECT ... WHERE status = ? ORDER BY created_at FOR UPDATE SKIP LOCKED).
-- Why: durable-notification-queue mission — the notifications table becomes the
--      queue itself, claimed by a poller instead of an in-memory channel.
-- Data impact: none. All new columns are nullable or defaulted, safe on a
--      non-empty table.

ALTER TABLE notifications
    ADD COLUMN locked_by VARCHAR(255),
    ADD COLUMN locked_at TIMESTAMP,
    ADD COLUMN attempts_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_notification_status_created_at ON notifications(status, created_at);
