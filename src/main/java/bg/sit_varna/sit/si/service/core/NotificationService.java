package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.exception.exceptions.DuplicateNotificationException;
import bg.sit_varna.sit.si.exception.exceptions.RateLimitException;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.redis.DeduplicationService;
import bg.sit_varna.sit.si.service.redis.RateLimitService;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    private static final String NOTIFICATIONS_PK_CONSTRAINT = "notifications_pkey";
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private final RateLimitService rateLimitService;
    private final DeduplicationService deduplicationService;
    private final MessageService messageService;
    private final NotificationRepository notificationRepository;

    @Inject
    public NotificationService(RateLimitService rateLimitService,
                                DeduplicationService deduplicationService,
                                MessageService messageService,
                                NotificationRepository notificationRepository) {
        this.rateLimitService = rateLimitService;
        this.deduplicationService = deduplicationService;
        this.messageService = messageService;
        this.notificationRepository = notificationRepository;
    }

    public void dispatchNotification(Notification request) {
        // 1. Rate Limiting
        checkRateLimit(request);

        // 2. Deduplication
        String contentKey = request.usesTemplate() ? request.getTemplateName() : request.getMessage();
        if (deduplicationService.isDuplicate(request.getRecipient(), request.getChannel(), contentKey)) {
            LOG.warnf("Skipping duplicate notification for %s", request.getRecipient());
            return;
        }

        // 3. Persistence is the entire dispatch: the notification-queue poller claims
        // QUEUED rows directly from this table (see QueuePoller), no in-memory hop.
        persistRecord(request);
    }

    public List<NotificationRecord> getFailedNotifications(int page, int size) {
        return notificationRepository.findByStatus(NotificationStatus.FAILED, Page.of(page, size)).list();
    }

    public long countFailedNotifications() {
        return notificationRepository.countByStatus(NotificationStatus.FAILED);
    }

    private void checkRateLimit(Notification request) {
        Locale locale = request.getLocale() != null ? Locale.forLanguageTag(request.getLocale()) : Locale.ENGLISH;
        if (!rateLimitService.isAllowed(request.getRecipient(), request.getChannel())) {
            long resetTime = rateLimitService.getResetTime(request.getRecipient(), request.getChannel());
            throw new RateLimitException(
                    messageService.getTitle(NotificationErrorCode.RATE_LIMIT_EXCEEDED, locale),
                    messageService.getMessage(NotificationErrorCode.RATE_LIMIT_EXCEEDED,
                            locale, request.getChannel().toString(), request.getRecipient(), resetTime),
                    resetTime
            );
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void persistRecord(Notification request) {
        NotificationRecord record = new NotificationRecord();
        record.setId(request.getId());
        record.setRecipient(request.getRecipient());
        record.setChannel(request.getChannel());
        record.setTemplateName(request.getTemplateName());
        record.setLocale(request.getLocale() != null ? Locale.forLanguageTag(request.getLocale()) : null);
        record.setMessage(request.getMessage());
        record.setStatus(NotificationStatus.QUEUED);
        record.setPayload(request.getData());

        try {
            notificationRepository.persist(record);
            // Forces the INSERT (and thus the constraint check) to happen here,
            // synchronously, instead of being deferred to commit time - after
            // this method (and its catch block) would already have returned.
            notificationRepository.flush();
        } catch (RuntimeException e) {
            ConstraintViolationException violation = findConstraintViolation(e);
            if (violation == null || !isDuplicateNotificationId(violation)) {
                throw e;
            }
            LOG.warnf("Duplicate notification id detected: %s", request.getId());
            Locale locale = request.getLocale() != null ? Locale.forLanguageTag(request.getLocale()) : Locale.ENGLISH;
            throw new DuplicateNotificationException(
                    messageService.getTitle(NotificationErrorCode.DUPLICATE_NOTIFICATION, locale),
                    messageService.getMessage(NotificationErrorCode.DUPLICATE_NOTIFICATION, locale, request.getId()));
        }
    }

    /**
     * Hibernate has been observed to throw this bare, but jakarta.persistence.*
     * wrappers are common enough across versions/paths that it's worth walking
     * the cause chain rather than assuming a fixed exception shape.
     *
     * Package-private (not private) so NotificationServiceDedupTest can drive
     * it directly with a fabricated ConstraintViolationException, covering
     * exception shapes real Hibernate/Postgres behavior won't reliably
     * reproduce in a test.
     */
    static ConstraintViolationException findConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * SQLState 23505 is the reliable, dialect-standard signal for a unique
     * violation, but not specific enough on its own - the constraint name
     * must also match this table's PK exactly. A null/unavailable constraint
     * name does NOT count as a match: failing closed (rethrow the original
     * exception) is safer than risking a future second unique constraint on
     * this table being silently folded into "duplicate notification id".
     */
    static boolean isDuplicateNotificationId(ConstraintViolationException violation) {
        return UNIQUE_VIOLATION_SQLSTATE.equals(violation.getSQLState())
                && NOTIFICATIONS_PK_CONSTRAINT.equals(violation.getConstraintName());
    }
}
