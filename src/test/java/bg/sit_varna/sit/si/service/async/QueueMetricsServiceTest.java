package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class QueueMetricsServiceTest extends BaseIntegrationTest {

    @Inject QueueMetricsService queueMetricsService;
    @Inject NotificationRepository notificationRepository;
    @Inject MeterRegistry registry;

    @Test
    void countersIncrementOnTriggeringEvents() {
        double claimedBefore = registry.get("notifications.queue.claimed.total").counter().count();
        double reapedBefore = registry.get("notifications.queue.reaped.total").counter().count();
        double poisonedBefore = registry.get("notifications.queue.poisoned.total").counter().count();

        queueMetricsService.recordClaimed(3);
        queueMetricsService.recordReaped(1);
        queueMetricsService.recordPoisoned();

        assertEquals(claimedBefore + 3, registry.get("notifications.queue.claimed.total").counter().count());
        assertEquals(reapedBefore + 1, registry.get("notifications.queue.reaped.total").counter().count());
        assertEquals(poisonedBefore + 1, registry.get("notifications.queue.poisoned.total").counter().count());
    }

    @Test
    void depthGaugeReflectsActualQueuedCount() {
        long before = notificationRepository.countByStatus(NotificationStatus.QUEUED);

        for (int i = 0; i < 3; i++) {
            String id = UUID.randomUUID().toString();
            QuarkusTransaction.requiringNew().run(() -> {
                NotificationRecord record = new NotificationRecord();
                record.setId(id);
                record.setRecipient("metrics-depth@example.com");
                record.setChannel(NotificationChannel.EMAIL);
                record.setStatus(NotificationStatus.QUEUED);
                notificationRepository.persist(record);
            });
        }

        double depth = registry.get("notifications.queue.depth").gauge().value();
        assertTrue(depth >= before + 3,
                "Expected queue depth gauge to reflect at least the 3 freshly-persisted rows, was " + depth);
    }

    @Test
    void oldestQueuedAgeGaugeReflectsActualDbState() {
        String id = UUID.randomUUID().toString();

        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("metrics-age@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.QUEUED);
            notificationRepository.persist(record);
        });

        // created_at is @CreationTimestamp/updatable=false, so it can't be set via the
        // entity - backdate it with a native update to simulate an old row.
        QuarkusTransaction.requiringNew().run(() ->
                notificationRepository.getEntityManager()
                        .createNativeQuery("UPDATE notifications SET created_at = created_at - INTERVAL '30 seconds' WHERE id = :id")
                        .setParameter("id", id)
                        .executeUpdate());

        double ageSeconds = registry.get("notifications.queue.oldest.age.seconds").gauge().value();
        assertTrue(ageSeconds >= 25, "Expected oldest-queued age to reflect the backdated row, was " + ageSeconds);
    }
}
