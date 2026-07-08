package bg.sit_varna.sit.si.scheduler;

import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.async.QueueMetricsService;
import bg.sit_varna.sit.si.service.redis.RedisRetryService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class RetryScheduler {

    private static final Logger LOG = Logger.getLogger(RetryScheduler.class);

    private final RedisRetryService redisRetryService;
    private final NotificationRepository notificationRepository;
    private final QueueConfig queueConfig;
    private final QueueMetricsService queueMetricsService;

    @Inject
    public RetryScheduler(RedisRetryService redisRetryService,
                           NotificationRepository notificationRepository,
                           QueueConfig queueConfig,
                           QueueMetricsService queueMetricsService) {
        this.redisRetryService = redisRetryService;
        this.notificationRepository = notificationRepository;
        this.queueConfig = queueConfig;
        this.queueMetricsService = queueMetricsService;
    }

    /**
     * Runs every 60 seconds to check for messages ready to be retried.
     */
    @Scheduled(every = "60s")
    public void processRetries() {
        List<Notification> dueNotifications = redisRetryService.fetchDueNotifications();

        if (!dueNotifications.isEmpty()) {
            LOG.infof("Resurrecting %d notifications from Redis Cold Queue", dueNotifications.size());

            for (Notification notification : dueNotifications) {
                // Flip back to QUEUED; the notification-queue poller claims it
                // through the normal claimBatch() path, not a direct hand-off.
                boolean requeued = notificationRepository.requeueIfBelowRetryCap(
                        notification.getId(), queueConfig.maxColdRetryCycles());
                if (!requeued) {
                    LOG.warnf("Notification %s exhausted %d cold-retry cycles - leaving as terminal FAILED",
                            notification.getId(), queueConfig.maxColdRetryCycles());
                    queueMetricsService.recordPoisoned();
                }
            }
        }
    }
}
