package bg.sit_varna.sit.si.qacommons.db;

/**
 * Only the columns the live oracle tests actually assert on - identity, not
 * status or the durable-queue poller's claim columns (locked_by/locked_at),
 * since those race with the poller and must never be asserted on.
 */
public record NotificationRow(String id, String recipient, String channel) {
}
