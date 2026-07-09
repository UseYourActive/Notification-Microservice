package bg.sit_varna.sit.si.service.core;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.exception.exceptions.RateLimitException;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.redis.DeduplicationService;
import bg.sit_varna.sit.si.service.redis.RateLimitService;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

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
        Locale locale = Locale.forLanguageTag(request.getLocale());
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
        if (notificationRepository.findById(request.getId()) != null) {
            return;
        }

        NotificationRecord record = new NotificationRecord();
        record.setId(request.getId());
        record.setRecipient(request.getRecipient());
        record.setChannel(request.getChannel());
        record.setTemplateName(request.getTemplateName());
        record.setLocale(request.getLocale() != null ? Locale.forLanguageTag(request.getLocale()) : null);
        record.setMessage(request.getMessage());
        record.setStatus(NotificationStatus.QUEUED);
        record.setPayload(request.getData());

        notificationRepository.persist(record);
    }
}
