package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Drives the durable notification queue: claims rows from Postgres (see
 * NotificationRepository.claimBatch) and dispatches each to NotificationProcessor on
 * its own virtual thread, replacing the previous in-memory SmallRye channel.
 * Concurrency is bounded here (via the semaphore below), not by the
 * @RunOnVirtualThread annotation on NotificationProcessor - that annotation is a
 * no-op safety net when the caller (this class's dispatch executor) is already
 * running on a virtual thread.
 */
@ApplicationScoped
public class QueuePoller {

    private static final Logger LOG = Logger.getLogger(QueuePoller.class);

    private final String instanceId = UUID.randomUUID().toString();
    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;
    private final QueueConfig queueConfig;
    private final Semaphore concurrencySlots;
    private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean claimingEnabled = true;

    @Inject
    public QueuePoller(NotificationRepository notificationRepository,
                        NotificationProcessor notificationProcessor,
                        QueueConfig queueConfig) {
        this.notificationRepository = notificationRepository;
        this.notificationProcessor = notificationProcessor;
        this.queueConfig = queueConfig;
        this.concurrencySlots = new Semaphore(queueConfig.workerConcurrency());
    }

    @Scheduled(every = "{queue.poll-interval}")
    void poll() {
        if (!claimingEnabled) {
            return;
        }

        int available = concurrencySlots.availablePermits();
        if (available <= 0) {
            return;
        }

        int limit = Math.min(available, queueConfig.batchSize());
        List<NotificationRepository.ClaimResult> claimed = notificationRepository.claimBatch(
                limit, instanceId, queueConfig.visibilityTimeout().toSeconds());

        for (NotificationRepository.ClaimResult result : claimed) {
            dispatch(result);
        }
    }

    void stopClaiming() {
        claimingEnabled = false;
    }

    private void dispatch(NotificationRepository.ClaimResult result) {
        try {
            concurrencySlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Notification notification = toNotification(result.notification());
        dispatchExecutor.submit(() -> {
            try {
                notificationProcessor.processNotification(notification);
            } catch (Exception e) {
                LOG.errorf(e, "Unhandled error dispatching notification %s", notification.getId());
            } finally {
                concurrencySlots.release();
            }
        });
    }

    private static Notification toNotification(NotificationRecord record) {
        return Notification.builder()
                .id(record.getId())
                .recipient(record.getRecipient())
                .channel(record.getChannel())
                .templateName(record.getTemplateName())
                .locale(record.getLocale())
                .data(record.getPayload())
                .message(record.getMessage())
                .build();
    }
}
