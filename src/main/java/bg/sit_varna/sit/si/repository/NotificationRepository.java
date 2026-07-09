package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.entity.NotificationRecord_;
import bg.sit_varna.sit.si.repository.sql.NotificationSql;
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

    private static final String SELECT_CLAIM_CANDIDATES = String.format("""
            SELECT %1$s, %2$s FROM %3$s
            WHERE %2$s = 'QUEUED'
               OR (%2$s = 'PROCESSING' AND %4$s < now() - (:visibilitySeconds * INTERVAL '1 second'))
            ORDER BY %5$s
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """,
            NotificationSql.COLUMN_ID, NotificationSql.COLUMN_STATUS, NotificationSql.TABLE,
            NotificationSql.COLUMN_LOCKED_AT, NotificationSql.COLUMN_CREATED_AT);

    private static final String UPDATE_CLAIMED = String.format("""
            UPDATE %1$s SET %2$s = 'PROCESSING', %3$s = :workerId, %4$s = now()
            WHERE %5$s IN (:ids)
            """,
            NotificationSql.TABLE, NotificationSql.COLUMN_STATUS, NotificationSql.COLUMN_LOCKED_BY,
            NotificationSql.COLUMN_LOCKED_AT, NotificationSql.COLUMN_ID);

    public List<NotificationRecord> findByRecipient(String recipient) {
        return find(NotificationRecord_.RECIPIENT, recipient).list();
    }

    public List<NotificationRecord> findByStatus(NotificationStatus status) {
        return find(NotificationRecord_.STATUS, status).list();
    }

    public PanacheQuery<NotificationRecord> findByStatus(NotificationStatus status, Page page) {
        return find(NotificationRecord_.STATUS, Sort.by(NotificationRecord_.CREATED_AT).descending(), status).page(page);
    }

    public long countByStatus(NotificationStatus status) {
        return count(NotificationRecord_.STATUS, status);
    }

    public Optional<LocalDateTime> findOldestQueuedCreatedAt() {
        return find(NotificationRecord_.STATUS, Sort.by(NotificationRecord_.CREATED_AT), NotificationStatus.QUEUED)
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
        List<Object[]> candidates = getEntityManager().createNativeQuery(SELECT_CLAIM_CANDIDATES)
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

        getEntityManager().createNativeQuery(UPDATE_CLAIMED)
                .setParameter("workerId", workerId)
                .setParameter("ids", ids)
                .executeUpdate();

        // The bulk native UPDATE above bypasses the persistence context, so clear it
        // before re-reading these rows to avoid returning stale cached entities.
        getEntityManager().clear();

        Map<String, NotificationRecord> byId = new LinkedHashMap<>();
        for (NotificationRecord record : list(NotificationRecord_.ID + " in ?1", ids)) {
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
