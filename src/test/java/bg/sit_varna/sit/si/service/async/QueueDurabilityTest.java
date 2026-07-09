package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.mapper.NotificationRecordMapper;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.channel.email.SendGridEmailSender;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * End-to-end durability checks for the Postgres-backed queue: crash-mid-processing
 * recovery, and no double-claim across concurrently-polling replicas. Runs against
 * the existing Testcontainers Postgres/Redis setup - no new containers.
 */
@QuarkusTest
public class QueueDurabilityTest extends BaseIntegrationTest {

    @Inject NotificationRepository notificationRepository;
    @Inject NotificationProcessor notificationProcessor;
    @Inject QueueConfig queueConfig;
    @Inject QueueMetricsService queueMetricsService;
    @Inject NotificationRecordMapper notificationRecordMapper;
    @ConfigProperty(name = "quarkus.shutdown.timeout")
    Duration shutdownTimeout;
    @InjectMock SendGridEmailSender sendGridEmailSender;

    @BeforeEach
    void setupMock() {
        Mockito.when(sendGridEmailSender.getProviderName()).thenReturn("sendgrid");
    }

    @Test
    void crashedRowIsReclaimedAndDeliveredExactlyOnce() {
        Mockito.doNothing().when(sendGridEmailSender).send(any(), any(), any(), any(), any(), any(), any());

        String id = UUID.randomUUID().toString();
        LocalDateTime staleLockedAt = LocalDateTime.now().minusSeconds(queueConfig.visibilityTimeout().toSeconds() + 30);
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("crash-recovery@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            record.setLockedBy("dead-worker");
            record.setLockedAt(staleLockedAt);
            record.setMessage("Hello");
            record.setLocale(Locale.forLanguageTag("en"));
            notificationRepository.persist(record);
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            NotificationStatus status = QuarkusTransaction.requiringNew()
                    .call(() -> notificationRepository.findById(id).getStatus());
            assertEquals(NotificationStatus.SENT, status);
        });

        Mockito.verify(sendGridEmailSender, Mockito.times(1))
                .send(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void twoConcurrentlyPollingReplicasNeverClaimTheSameRow() throws Exception {
        Map<String, AtomicInteger> sendCountByRecipient = new ConcurrentHashMap<>();
        Mockito.doAnswer(invocation -> {
                    String recipient = invocation.getArgument(0, String.class);
                    sendCountByRecipient.computeIfAbsent(recipient, r -> new AtomicInteger()).incrementAndGet();
                    return null;
                }).when(sendGridEmailSender)
                .send(any(), any(), any(), any(), any(), any(), any());

        int rowCount = 15;
        List<String> recipients = new java.util.ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            String recipient = "replica-race-" + UUID.randomUUID() + "@example.com";
            recipients.add(recipient);
            QuarkusTransaction.requiringNew().run(() -> {
                NotificationRecord record = new NotificationRecord();
                record.setId(UUID.randomUUID().toString());
                record.setRecipient(recipient);
                record.setChannel(NotificationChannel.EMAIL);
                record.setStatus(NotificationStatus.QUEUED);
                record.setMessage("Hello");
                record.setLocale(Locale.forLanguageTag("en"));
                notificationRepository.persist(record);
            });
        }

        // A second "replica" poller, racing against the app's own live-scheduled
        // QueuePoller instance for the same claimable rows.
        QueuePoller secondReplica = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, shutdownTimeout, queueMetricsService, notificationRecordMapper);

        long pollUntil = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < pollUntil) {
            secondReplica.poll();
            Thread.sleep(20);
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long sentCount = QuarkusTransaction.requiringNew().call(() ->
                    recipients.stream()
                            .filter(r -> notificationRepository.find("recipient", r).firstResultOptional()
                                    .map(n -> n.getStatus() == NotificationStatus.SENT)
                                    .orElse(false))
                            .count());
            assertEquals(rowCount, sentCount, "All rows should eventually be delivered");
        });

        for (String recipient : recipients) {
            int count = sendCountByRecipient.getOrDefault(recipient, new AtomicInteger()).get();
            assertTrue(count == 1, "Recipient " + recipient + " should be sent exactly once, was sent " + count + " times");
        }
    }
}
