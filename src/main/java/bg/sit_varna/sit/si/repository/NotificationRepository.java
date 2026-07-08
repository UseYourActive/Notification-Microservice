package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<NotificationRecord, String> {

    public List<NotificationRecord> findByRecipient(String recipient) {
        return find("recipient", recipient).list();
    }

    public List<NotificationRecord> findByStatus(NotificationStatus status) {
        return find("status", status).list();
    }

    public PanacheQuery<NotificationRecord> findByStatus(NotificationStatus status, Page page) {
        return find("status", Sort.by("createdAt").descending(), status).page(page);
    }

    public long countByStatus(NotificationStatus status) {
        return count("status", status);
    }

    public Optional<LocalDateTime> findOldestQueuedCreatedAt() {
        return find("status", Sort.by("createdAt"), NotificationStatus.QUEUED)
                .firstResultOptional()
                .map(NotificationRecord::getCreatedAt);
    }

    /**
     * Atomically claims up to {@code limit} rows for {@code workerId}: fresh QUEUED
     * rows, plus PROCESSING rows whose claim is older than {@code visibilityTimeoutSeconds}
     * (crash recovery). Uses SELECT ... FOR UPDATE SKIP LOCKED so concurrent callers
     * (replicas) never claim the same row.
     */
    @Transactional
    public List<ClaimResult> claimBatch(int limit, String workerId, long visibilityTimeoutSeconds) {
        List<Object[]> candidates = getEntityManager().createNativeQuery(
                        "SELECT id, status FROM notifications " +
                                "WHERE status = 'QUEUED' " +
                                "   OR (status = 'PROCESSING' AND locked_at < now() - (:visibilitySeconds * INTERVAL '1 second')) " +
                                "ORDER BY created_at " +
                                "FOR UPDATE SKIP LOCKED " +
                                "LIMIT :limit")
                .setParameter("visibilitySeconds", visibilityTimeoutSeconds)
                .setParameter("limit", limit)
                .getResultList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        Map<String, Boolean> reapedById = new HashMap<>();
        for (Object[] row : candidates) {
            String id = (String) row[0];
            String status = (String) row[1];
            ids.add(id);
            reapedById.put(id, NotificationStatus.PROCESSING.name().equals(status));
        }

        getEntityManager().createNativeQuery(
                        "UPDATE notifications SET status = 'PROCESSING', locked_by = :workerId, locked_at = now() " +
                                "WHERE id IN (:ids)")
                .setParameter("workerId", workerId)
                .setParameter("ids", ids)
                .executeUpdate();

        // The bulk native UPDATE above bypasses the persistence context, so clear it
        // before re-reading these rows to avoid returning stale cached entities.
        getEntityManager().clear();

        Map<String, NotificationRecord> byId = new LinkedHashMap<>();
        for (NotificationRecord record : list("id in ?1", ids)) {
            byId.put(record.getId(), record);
        }

        List<ClaimResult> results = new ArrayList<>();
        for (String id : ids) {
            NotificationRecord record = byId.get(id);
            if (record != null) {
                results.add(new ClaimResult(record, reapedById.get(id)));
            }
        }
        return results;
    }

    public record ClaimResult(NotificationRecord notification, boolean reaped) {
    }

    /**
     * Cold-queue resurrection: flips a row back to QUEUED so the poller claims it
     * through the normal claimBatch() path, unless it has already exhausted
     * maxCycles full Layer-1+cold-queue cycles (see
     * NotificationStateService.recordColdQueueCycle), in which case it's left as a
     * terminal FAILED and this returns false. Concurrent resurrection by multiple
     * replicas is safe - the flip itself is idempotent (every replica's
     * RetryScheduler does the same flip based on the same persisted attempts_count),
     * and claimBatch()'s FOR UPDATE SKIP LOCKED ensures only one replica ever wins
     * the actual claim regardless of how many flipped the status.
     *
     * @return true if the row was requeued, false if it was left terminally FAILED
     *         (cap reached) or didn't exist.
     */
    @Transactional
    public boolean requeueIfBelowRetryCap(String id, int maxCycles) {
        NotificationRecord record = findById(id);
        if (record == null) {
            return false;
        }
        if (record.getAttemptsCount() >= maxCycles) {
            return false;
        }
        record.setStatus(NotificationStatus.QUEUED);
        record.setLockedBy(null);
        record.setLockedAt(null);
        return true;
    }
}
