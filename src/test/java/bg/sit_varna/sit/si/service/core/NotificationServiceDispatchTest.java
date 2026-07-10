package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.exception.exceptions.DuplicateNotificationException;
import bg.sit_varna.sit.si.exception.exceptions.RateLimitException;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.redis.DeduplicationService;
import bg.sit_varna.sit.si.service.redis.RateLimitService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit tests for {@link NotificationService}, covering the
 * branches not already exercised by {@code NotificationServiceDedupTest}
 * (which drives the real-Postgres duplicate-id path via {@code @QuarkusTest}):
 * the rate-limit short-circuit, the request-locale ternaries (both null and
 * non-null), and every reachable combination of
 * {@code violation == null || !isDuplicateNotificationId(violation)} in the
 * persist-failure catch block.
 *
 * <p>{@code persistRecord} is package-private-callable (protected, same
 * package) and this test constructs {@link NotificationService} directly with
 * {@code new}, not via a CDI proxy, so its {@code @Transactional} annotation
 * is inert metadata here - no transaction manager is involved, and the mocked
 * {@link NotificationRepository} is safe to call directly.
 */
class NotificationServiceDispatchTest {

    private RateLimitService rateLimitService;
    private DeduplicationService deduplicationService;
    private MessageService messageService;
    private NotificationRepository notificationRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        deduplicationService = mock(DeduplicationService.class);
        messageService = mock(MessageService.class);
        notificationRepository = mock(NotificationRepository.class);

        when(messageService.getTitle(any(NotificationErrorCode.class), any(Locale.class)))
                .thenReturn("title");
        when(messageService.getMessage(any(NotificationErrorCode.class), any(Locale.class), any()))
                .thenReturn("message");

        notificationService = new NotificationService(
                rateLimitService, deduplicationService, messageService, notificationRepository);
    }

    // ---------------------------------------------------------------
    // checkRateLimit: rate limit exceeded short-circuits before dedup/persist,
    // exercised with both a null and a non-null request locale (line 70 ternary).
    // ---------------------------------------------------------------

    @Test
    void dispatchNotification_rateLimitExceededWithNullLocale_throwsRateLimitExceptionAndNeverPersists() {
        Notification request = notification("id-1", null);
        when(rateLimitService.isAllowed("recipient@example.com", NotificationChannel.EMAIL)).thenReturn(false);
        when(rateLimitService.getResetTime("recipient@example.com", NotificationChannel.EMAIL)).thenReturn(30L);

        assertThrows(RateLimitException.class, () -> notificationService.dispatchNotification(request));

        verify(messageService).getTitle(NotificationErrorCode.RATE_LIMIT_EXCEEDED, Locale.ENGLISH);
        verifyNoInteractions(deduplicationService);
        verify(notificationRepository, never()).persist(any(NotificationRecord.class));
        verify(notificationRepository, never()).flush();
    }

    @Test
    void dispatchNotification_rateLimitExceededWithNonNullLocale_throwsRateLimitException() {
        Notification request = notification("id-2", "bg");
        when(rateLimitService.isAllowed("recipient@example.com", NotificationChannel.EMAIL)).thenReturn(false);
        when(rateLimitService.getResetTime("recipient@example.com", NotificationChannel.EMAIL)).thenReturn(45L);

        assertThrows(RateLimitException.class, () -> notificationService.dispatchNotification(request));

        verify(messageService).getTitle(NotificationErrorCode.RATE_LIMIT_EXCEEDED, Locale.forLanguageTag("bg"));
        verify(notificationRepository, never()).persist(any(NotificationRecord.class));
    }

    // ---------------------------------------------------------------
    // Happy path: allowed, not a duplicate, persist succeeds cleanly.
    // Also exercises the persistRecord locale ternary (line 89) for both
    // null and non-null request locale.
    // ---------------------------------------------------------------

    @Test
    void dispatchNotification_allowedAndNotDuplicate_persistsSuccessfully() {
        Notification request = notification("id-3", null);
        when(rateLimitService.isAllowed(anyString(), any(NotificationChannel.class))).thenReturn(true);
        when(deduplicationService.isDuplicate(anyString(), any(NotificationChannel.class), anyString()))
                .thenReturn(false);

        notificationService.dispatchNotification(request);

        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRepository).persist(captor.capture());
        verify(notificationRepository).flush();
        assertNull(captor.getValue().getLocale());
        assertEquals("id-3", captor.getValue().getId());
    }

    @Test
    void dispatchNotification_duplicateAccordingToDeduplicationService_skipsPersistSilently() {
        Notification request = notification("id-4", null);
        when(rateLimitService.isAllowed(anyString(), any(NotificationChannel.class))).thenReturn(true);
        when(deduplicationService.isDuplicate(anyString(), any(NotificationChannel.class), anyString()))
                .thenReturn(true);

        notificationService.dispatchNotification(request);

        verify(notificationRepository, never()).persist(any(NotificationRecord.class));
        verify(notificationRepository, never()).flush();
    }

    @Test
    void persistRecord_withNonNullLocale_mapsRequestLocaleOntoTheRecord() {
        Notification request = notification("id-5", "fr");

        notificationService.persistRecord(request);

        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRepository).persist(captor.capture());
        verify(notificationRepository).flush();
        assertEquals(Locale.forLanguageTag("fr"), captor.getValue().getLocale());
    }

    // ---------------------------------------------------------------
    // persistRecord catch block: violation == null || !isDuplicateNotificationId(violation)
    // ---------------------------------------------------------------

    @Test
    void persistRecord_genericRuntimeExceptionWithNoConstraintViolationCause_isRethrownAsIs() {
        Notification request = notification("id-6", null);
        RuntimeException boom = new RuntimeException("boom");
        doThrow(boom).when(notificationRepository).persist(any(NotificationRecord.class));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> notificationService.persistRecord(request));

        assertSame(boom, thrown);
        verify(messageService, never()).getTitle(any(NotificationErrorCode.class), any(Locale.class));
    }

    @Test
    void persistRecord_constraintViolationOnADifferentConstraint_isRethrownAsIsNotWrappedAsDuplicate() {
        Notification request = notification("id-7", null);
        ConstraintViolationException violation = new ConstraintViolationException(
                "simulated", new SQLException("simulated", "23505"), "uk_notifications_some_other_column");
        RuntimeException wrapper = new RuntimeException("wrapped", violation);
        doThrow(wrapper).when(notificationRepository).persist(any(NotificationRecord.class));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> notificationService.persistRecord(request));

        assertSame(wrapper, thrown);
    }

    @Test
    void persistRecord_duplicatePrimaryKeyViolationWithNullLocale_throwsDuplicateNotificationException() {
        Notification request = notification("id-8", null);
        ConstraintViolationException violation = new ConstraintViolationException(
                "simulated", new SQLException("simulated", "23505"), "notifications_pkey");
        RuntimeException wrapper = new RuntimeException("wrapped", violation);
        doThrow(wrapper).when(notificationRepository).persist(any(NotificationRecord.class));

        assertThrows(DuplicateNotificationException.class, () -> notificationService.persistRecord(request));

        verify(messageService).getTitle(NotificationErrorCode.DUPLICATE_NOTIFICATION, Locale.ENGLISH);
    }

    @Test
    void persistRecord_duplicatePrimaryKeyViolationWithNonNullLocale_throwsDuplicateNotificationException() {
        Notification request = notification("id-9", "de");
        ConstraintViolationException violation = new ConstraintViolationException(
                "simulated", new SQLException("simulated", "23505"), "notifications_pkey");
        RuntimeException wrapper = new RuntimeException("wrapped", violation);
        doThrow(wrapper).when(notificationRepository).persist(any(NotificationRecord.class));

        assertThrows(DuplicateNotificationException.class, () -> notificationService.persistRecord(request));

        verify(messageService).getTitle(NotificationErrorCode.DUPLICATE_NOTIFICATION, Locale.forLanguageTag("de"));
    }

    private static Notification notification(String id, String locale) {
        Notification.Builder builder = Notification.builder()
                .id(id)
                .recipient("recipient@example.com")
                .channel(NotificationChannel.EMAIL)
                .message("hello")
                .data(Map.of());
        // The builder defaults `locale` to "en"; an explicit call (including
        // an explicit null) is required to reach both ternary branches under test.
        builder.locale(locale);
        return builder.build();
    }
}
