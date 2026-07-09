package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class NotificationStateServiceTest extends BaseIntegrationTest {

    @Inject NotificationStateService stateService;
    @Inject NotificationRepository notificationRepository;

    @Test
    void recordAttemptFailureLeavesRowClaimableInsteadOfTerminal() {
        String id = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("retry-state@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            notificationRepository.persist(record);
        });

        stateService.recordAttemptFailure(id, "simulated transient failure", null);

        // Read status and attempt count inside the transaction - `attempts` is a
        // lazy collection and would throw LazyInitializationException once the
        // session closes.
        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        int attemptCount = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getAttempts().size());

        // This is the property the mission's crash-recovery guarantee depends on: a
        // retriable attempt failure must not look terminal to claimBatch()'s reaper
        // (which only reclaims QUEUED/stale-PROCESSING rows, never FAILED) - if the
        // process crashes during @Retry's backoff, the row must still be reclaimable.
        assertEquals(NotificationStatus.PROCESSING, status,
                "recordAttemptFailure must not flip status away from PROCESSING");
        assertEquals(1, attemptCount,
                "the failed attempt should still be recorded for the audit trail");
    }

    @Test
    void updateStatusStillPerformsATerminalTransition() {
        String id = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("terminal-state@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            notificationRepository.persist(record);
        });

        stateService.updateStatus(id, NotificationStatus.FAILED, "exhausted", null);

        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        assertEquals(NotificationStatus.FAILED, status);
    }
}
