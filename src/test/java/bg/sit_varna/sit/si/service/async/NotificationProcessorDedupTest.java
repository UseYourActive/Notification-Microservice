package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.channel.email.SendGridEmailSender;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
public class NotificationProcessorDedupTest extends BaseIntegrationTest {

    @Inject NotificationProcessor notificationProcessor;
    @Inject NotificationRepository notificationRepository;
    @InjectMock SendGridEmailSender sendGridEmailSender;

    @BeforeEach
    void setupMock() {
        Mockito.when(sendGridEmailSender.getProviderName()).thenReturn("sendgrid");
    }

    @Test
    void testDoubleClaimResultsInExactlyOneSend() {
        Mockito.doNothing().when(sendGridEmailSender).send(any(), any(), any(), any(), any(), any(), any());

        String id = UUID.randomUUID().toString();
        persistQueuedRecord(id);
        Notification notification = buildNotification(id);

        // Simulate a reaper reclaiming and redispatching a row that was already sent.
        notificationProcessor.processNotification(notification);
        notificationProcessor.processNotification(notification);

        Mockito.verify(sendGridEmailSender, Mockito.times(1))
                .send(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testFailedThenRetriedNotificationStillSends() {
        // First attempt throws, every subsequent attempt (Layer-1 @Retry) succeeds.
        Mockito.doThrow(new RuntimeException("transient failure"))
                .doNothing()
                .when(sendGridEmailSender).send(any(), any(), any(), any(), any(), any(), any());

        String id = UUID.randomUUID().toString();
        persistQueuedRecord(id);
        Notification notification = buildNotification(id);

        notificationProcessor.processNotification(notification);

        Mockito.verify(sendGridEmailSender, Mockito.atLeast(2))
                .send(any(), any(), any(), any(), any(), any(), any());

        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        assertEquals(NotificationStatus.SENT, status,
                "The send-guard must not have blocked the eventual successful retry");
    }

    private void persistQueuedRecord(String id) {
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("dedup-send@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.QUEUED);
            record.setMessage("Hello from test");
            record.setLocale("en");
            notificationRepository.persist(record);
        });
    }

    private static Notification buildNotification(String id) {
        return Notification.builder()
                .id(id)
                .recipient("dedup-send@example.com")
                .channel(NotificationChannel.EMAIL)
                .message("Hello from test")
                .locale("en")
                .build();
    }
}
