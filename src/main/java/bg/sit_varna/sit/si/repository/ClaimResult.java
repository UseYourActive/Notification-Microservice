package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.entity.NotificationRecord;

/**
 * Result of a single row claimed by {@link NotificationRepository#claimBatch}.
 * {@code reaped} is true when the row was recovered from a stale PROCESSING lock
 * (crash recovery) rather than claimed fresh from QUEUED.
 */
public record ClaimResult(NotificationRecord notification, boolean reaped) {
}
