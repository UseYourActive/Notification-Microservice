package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.exception.exceptions.DuplicateNotificationException;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NotificationService.persistRecord() used to guard duplicate ids with a
 * check-then-act (findById == null, then persist) - a TOCTOU race that also
 * let a genuine collision surface as a raw, unhandled exception instead of a
 * clean response. These tests exercise the real Postgres unique-constraint
 * path directly (dispatchNotification() with a repeated id), since the REST
 * layer itself always mints a fresh UUID per request and can never produce a
 * real collision through normal HTTP traffic - the id-collision behavior is
 * only reachable by constructing the Notification domain object directly, as
 * done here.
 */
@QuarkusTest
class NotificationServiceDedupTest extends BaseIntegrationTest {

    @Inject
    NotificationService notificationService;
    @Inject
    NotificationRepository notificationRepository;

    @Test
    void secondDispatchWithADuplicateIdThrowsA409MappedException() {
        String id = UUID.randomUUID().toString();

        notificationService.dispatchNotification(notification(id, "first@example.com"));

        DuplicateNotificationException exception = assertThrows(DuplicateNotificationException.class,
                () -> notificationService.dispatchNotification(notification(id, "second@example.com")));

        assertEquals(Response.Status.CONFLICT.getStatusCode(), exception.getStatusCode(),
                "NotificationExceptionHandler maps this status directly onto the HTTP response");
        assertEquals("NOTIF_081", exception.getCode());
    }

    @Test
    void theFirstRecordIsUnaffectedByTheRejectedDuplicate() {
        String id = UUID.randomUUID().toString();

        notificationService.dispatchNotification(notification(id, "kept@example.com"));

        assertThrows(DuplicateNotificationException.class,
                () -> notificationService.dispatchNotification(notification(id, "rejected@example.com")));

        NotificationStatus status = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getStatus());
        String recipient = QuarkusTransaction.requiringNew()
                .call(() -> notificationRepository.findById(id).getRecipient());

        assertEquals(NotificationStatus.QUEUED, status);
        assertEquals("kept@example.com", recipient, "the rejected duplicate must not overwrite the original row");
    }

    @Test
    void anUnrelatedConstraintViolationIsNotTreatedAsADuplicate() {
        // recipient is NOT NULL at the DB level (V1.0.0) - this is a real,
        // different constraint (SQLState 23502, not 23505) and must surface
        // as-is, not be reinterpreted as "duplicate notification".
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .recipient(null)
                .channel(NotificationChannel.EMAIL)
                .message("no recipient")
                .locale("en")
                .data(Map.of())
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> notificationService.dispatchNotification(notification));

        Throwable cause = exception;
        while (cause != null && !(cause instanceof ConstraintViolationException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(ConstraintViolationException.class, cause);
        assertEquals("23502", ((ConstraintViolationException) cause).getSQLState());
    }

    private static Notification notification(String id, String recipient) {
        return Notification.builder()
                .id(id)
                .recipient(recipient)
                .channel(NotificationChannel.EMAIL)
                .message("hello")
                .locale("en")
                .data(Map.of())
                .build();
    }
}
