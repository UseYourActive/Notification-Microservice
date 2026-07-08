package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationAttempt;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NotificationStateService {

    private static final Logger LOG = Logger.getLogger(NotificationStateService.class);

    private final NotificationRepository notificationRepository;

    @Inject
    public NotificationStateService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void updateStatus(String id, NotificationStatus status, String message, String providerResponse) {
        if (id == null) return;

        NotificationRecord record = notificationRepository.findById(id);
        if (record == null) {
            LOG.warnf("Attempted to update status for unknown notification: %s", id);
            return;
        }

        record.setStatus(status);

        NotificationAttempt attempt = new NotificationAttempt();
        attempt.setStatus(status);
        attempt.setNotification(record);

        attempt.setErrorMessage(truncate(message));
        attempt.setProviderResponse(truncate(providerResponse));

        record.getAttempts().add(attempt);
        LOG.debugf("Updated notification %s to %s", id, status);
    }

    /**
     * Records a single retriable attempt failure without touching the row's overall
     * status - it stays PROCESSING. Deliberately NOT the same as updateStatus(...,
     * FAILED, ...): @Retry re-invokes processNotification() on the same thread for
     * each attempt, so writing a terminal-looking FAILED here would leave the row
     * permanently unrecoverable if the process crashes during the retry backoff
     * window (claimBatch's reaper only looks at QUEUED/stale-PROCESSING, never
     * FAILED). Leaving status as PROCESSING lets that same reaper recover a
     * mid-retry crash naturally. Only fallbackToRedis() (after retries are fully
     * exhausted) writes a real terminal FAILED.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordAttemptFailure(String id, String message, String providerResponse) {
        if (id == null) return;

        NotificationRecord record = notificationRepository.findById(id);
        if (record == null) {
            LOG.warnf("Attempted to record an attempt for unknown notification: %s", id);
            return;
        }

        NotificationAttempt attempt = new NotificationAttempt();
        attempt.setStatus(NotificationStatus.FAILED);
        attempt.setNotification(record);
        attempt.setErrorMessage(truncate(message));
        attempt.setProviderResponse(truncate(providerResponse));

        record.getAttempts().add(attempt);
        LOG.debugf("Recorded a failed attempt for notification %s (status left as %s)", id, record.getStatus());
    }

    /**
     * Marks one full Layer-1 (@Retry) + cold-queue cycle as exhausted. Called once
     * per fallbackToRedis() invocation - not per individual @Retry attempt - so
     * RetryScheduler can cap how many times a permanently-failing notification is
     * resurrected (see NotificationRepository.requeueIfBelowRetryCap).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordColdQueueCycle(String id) {
        if (id == null) return;

        NotificationRecord record = notificationRepository.findById(id);
        if (record == null) {
            return;
        }
        record.setAttemptsCount(record.getAttemptsCount() + 1);
    }

    private String truncate(String input) {
        if (input != null && input.length() > 1024) {
            return input.substring(0, 1024);
        }
        return input;
    }
}
