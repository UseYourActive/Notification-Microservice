package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.config.app.ApplicationConfig;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.service.channel.strategies.ChannelStrategy;
import bg.sit_varna.sit.si.service.channel.strategies.ChannelStrategyFactory;
import bg.sit_varna.sit.si.service.core.NotificationStateService;
import bg.sit_varna.sit.si.service.redis.DeduplicationService;
import bg.sit_varna.sit.si.service.redis.MetricsService;
import bg.sit_varna.sit.si.service.redis.RedisRetryService;
import bg.sit_varna.sit.si.template.core.TemplateService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Called directly by QueuePoller (one call per claimed row) - never invoke
 * processNotification via self-invocation (this.processNotification(...)); it must
 * go through the injected CDI bean so the @Retry/@Fallback interceptors apply.
 * Graceful shutdown (stop claiming + drain in-flight work) is owned by QueuePoller,
 * which can see both the claim loop and this class's in-flight counter.
 */
@ApplicationScoped
public class NotificationProcessor {

    private static final Logger LOG = Logger.getLogger(NotificationProcessor.class);

    private final ApplicationConfig applicationConfig;
    private final RedisRetryService redisRetryService;
    private final TemplateService templateService;
    private final MetricsService metricsService;
    private final ChannelStrategyFactory channelStrategyFactory;
    private final NotificationStateService stateService;
    private final DeduplicationService deduplicationService;

    private final AtomicInteger inFlightCount = new AtomicInteger();

    @Inject
    public NotificationProcessor(ApplicationConfig applicationConfig,
                                  RedisRetryService redisRetryService,
                                  TemplateService templateService,
                                  MetricsService metricsService,
                                  ChannelStrategyFactory channelStrategyFactory,
                                  NotificationStateService stateService,
                                  DeduplicationService deduplicationService) {
        this.applicationConfig = applicationConfig;
        this.redisRetryService = redisRetryService;
        this.templateService = templateService;
        this.metricsService = metricsService;
        this.channelStrategyFactory = channelStrategyFactory;
        this.stateService = stateService;
        this.deduplicationService = deduplicationService;
    }

    public int getInFlightCount() {
        return inFlightCount.get();
    }

    @RunOnVirtualThread
    @ActivateRequestContext
    @Retry // Layer 1: Fast in-memory retry (configured in application.properties)
    @Fallback(fallbackMethod = "fallbackToRedis") // Layer 2: If Layer 1 fails, goes here
    public void processNotification(Notification notification) {
        LOG.infof("Processing async notification [%s] for: %s via %s",
                notification.getId(), notification.getRecipient(), notification.getChannel());
        MDC.put("notificationId", notification.getId());

        if (deduplicationService.isAlreadySent(notification.getId())) {
            LOG.warnf("Notification %s was already sent - skipping duplicate send " +
                    "(likely a reaper reclaim of a row that wasn't actually dead)", notification.getId());
            return;
        }

        inFlightCount.incrementAndGet();
        try {
            // Render Template
            String processedContent = processContent(notification);
            notification.setProcessedContent(processedContent);

            ChannelStrategy strategy = channelStrategyFactory.getStrategy(notification.getChannel())
                    .orElseThrow(() -> new UnsupportedOperationException(
                            "No strategy configured for channel: " + notification.getChannel()));

            strategy.send(notification);
            // Mark the send-guard only after send() succeeds - never before - so a
            // failed attempt (this one throws below to @Retry/@Fallback) remains
            // retryable instead of being permanently blocked by its own guard.
            deduplicationService.markSent(notification.getId());

            stateService.updateStatus(
                    notification.getId(),
                    NotificationStatus.SENT,
                    "Sent successfully via " + strategy.getChannel(),
                    null
            );

            metricsService.recordNotification(notification.getChannel(), NotificationStatus.SENT);

            LOG.infof("Async processing completed for: %s", notification.getRecipient());
        } catch (Exception e) {
            LOG.error("Failed to process notification", e);
            stateService.updateStatus(notification.getId(), NotificationStatus.FAILED, e.getMessage(), null);
            throw e;
        } finally {
            inFlightCount.decrementAndGet();
            MDC.remove("notificationId");
        }
    }

    /**
     * Fallback method called when all @Retry attempts fail.
     * Moves the notification to the Redis "Cold Queue" for later retrial.
     */
    public void fallbackToRedis(Notification notification) {
        MDC.put("notificationId", notification.getId());

        try {
            LOG.warnf("All immediate retries failed for notification [%s]. Moving to Redis Cold Queue.",
                    notification.getId());

            stateService.updateStatus(
                    notification.getId(),
                    NotificationStatus.FAILED,
                    "Immediate retries exhausted. Moved to Redis queue.",
                    null
            );
            stateService.recordColdQueueCycle(notification.getId());

            metricsService.recordNotification(notification.getChannel(), NotificationStatus.FAILED);

            // Schedule for 5 minutes later (Cold Retry)
            redisRetryService.scheduleRetry(notification, 300);
        } finally {
            MDC.remove("notificationId");
        }
    }

    private String processContent(Notification request) {
        if (request.getTemplateName() != null && !request.getTemplateName().isBlank()) {
            return templateService.renderTemplate(
                    request.getTemplateName(),
                    request.getLocale() != null ? request.getLocale() : applicationConfig.defaultLocale(),
                    request.getData()
            );
        }
        return request.getMessage();
    }
}
