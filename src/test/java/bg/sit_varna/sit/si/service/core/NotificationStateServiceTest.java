package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.testkit.base.DatabaseTestBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static bg.sit_varna.sit.si.testkit.assertions.NotificationAssert.assertThatNotification;
import static bg.sit_varna.sit.si.testkit.mother.NotificationRecordMother.aNotification;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class NotificationStateServiceTest extends DatabaseTestBase {

    @Inject NotificationStateService stateService;
    @Inject NotificationRepository notificationRepository;

    @Test
    void recordAttemptFailureLeavesRowClaimableInsteadOfTerminal() {
        NotificationRecord seed = aNotification().processing().build();
        String id = seed.getId();
        QuarkusTransaction.requiringNew().run(() -> notificationRepository.persist(seed));

        stateService.recordAttemptFailure(id, "simulated transient failure", null);

        // `attempts` is a lazy collection and would throw LazyInitializationException
        // once the session closes, so its size must be read inside the transaction.
        int attemptCount = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getAttempts().size());
        NotificationRecord reloaded = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id));

        // This is the property the mission's crash-recovery guarantee depends on: a
        // retriable attempt failure must not look terminal to claimBatch()'s reaper
        // (which only reclaims QUEUED/stale-PROCESSING rows, never FAILED) - if the
        // process crashes during @Retry's backoff, the row must still be reclaimable.
        assertThatNotification(reloaded).hasStatus(NotificationStatus.PROCESSING);
        assertEquals(1, attemptCount,
                "the failed attempt should still be recorded for the audit trail");
    }

    @Test
    void updateStatusStillPerformsATerminalTransition() {
        NotificationRecord seed = aNotification().processing().build();
        String id = seed.getId();
        QuarkusTransaction.requiringNew().run(() -> notificationRepository.persist(seed));

        stateService.updateStatus(id, NotificationStatus.FAILED, "exhausted", null);

        NotificationRecord reloaded = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id));
        assertThatNotification(reloaded).isTerminal().hasStatus(NotificationStatus.FAILED);
    }
}
