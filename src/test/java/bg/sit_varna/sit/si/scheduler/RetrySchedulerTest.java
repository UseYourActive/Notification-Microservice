package bg.sit_varna.sit.si.scheduler;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.redis.RedisRetryService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

@QuarkusTest
public class RetrySchedulerTest extends BaseIntegrationTest {

    @Inject RetryScheduler retryScheduler;
    @Inject NotificationRepository notificationRepository;
    @Inject QueueConfig queueConfig;
    @InjectMock RedisRetryService redisRetryService;

    @Test
    void testResurrectsWhenBelowRetryCap() {
        String id = UUID.randomUUID().toString();
        persistFailedRecord(id, queueConfig.maxColdRetryCycles() - 1);

        Mockito.when(redisRetryService.fetchDueNotifications())
                .thenReturn(List.of(dueNotification(id)));

        retryScheduler.processRetries();

        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        Assertions.assertEquals(NotificationStatus.QUEUED, status,
                "A notification below the retry cap should be resurrected to QUEUED");
    }

    @Test
    void testStopsResurrectingAtRetryCap() {
        String id = UUID.randomUUID().toString();
        persistFailedRecord(id, queueConfig.maxColdRetryCycles());

        Mockito.when(redisRetryService.fetchDueNotifications())
                .thenReturn(List.of(dueNotification(id)));

        retryScheduler.processRetries();

        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        Assertions.assertEquals(NotificationStatus.FAILED, status,
                "A notification at the retry cap must stay terminally FAILED, not be resurrected");
    }

    private void persistFailedRecord(String id, int attemptsCount) {
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("poison@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.FAILED);
            record.setAttemptsCount(attemptsCount);
            notificationRepository.persist(record);
        });
    }

    private static Notification dueNotification(String id) {
        return Notification.builder()
                .id(id)
                .recipient("poison@example.com")
                .channel(NotificationChannel.EMAIL)
                .message("irrelevant for this test")
                .build();
    }
}
