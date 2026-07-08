package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final Duration DRAIN_POLL_INTERVAL = Duration.ofMillis(50);

    private final String instanceId = UUID.randomUUID().toString();
    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;
    private final QueueConfig queueConfig;
    private final Duration shutdownTimeout;
    private final QueueMetricsService queueMetricsService;
    private final Semaphore concurrencySlots;
    private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger inFlightCount = new AtomicInteger();

    private volatile boolean claimingEnabled = true;

    @Inject
    public QueuePoller(NotificationRepository notificationRepository,
                        NotificationProcessor notificationProcessor,
                        QueueConfig queueConfig,
                        @ConfigProperty(name = "quarkus.shutdown.timeout") Duration shutdownTimeout,
                        QueueMetricsService queueMetricsService) {
        this.notificationRepository = notificationRepository;
        this.notificationProcessor = notificationProcessor;
        this.queueConfig = queueConfig;
        this.shutdownTimeout = shutdownTimeout;
        this.queueMetricsService = queueMetricsService;
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

        int reapedCount = (int) claimed.stream().filter(NotificationRepository.ClaimResult::reaped).count();
        queueMetricsService.recordClaimed(claimed.size());
        queueMetricsService.recordReaped(reapedCount);

        for (NotificationRepository.ClaimResult result : claimed) {
            dispatch(result);
        }
    }

    /**
     * Stops claiming new batches immediately, then blocks until no dispatched row is
     * still in flight, bounded by quarkus.shutdown.timeout - so a SIGTERM/rolling
     * deploy drains what's already running instead of dropping it. Tracked here, at
     * the dispatch boundary, spanning a row's entire claim (including @Retry's
     * inter-attempt sleeps) - not inside NotificationProcessor, which @Retry
     * re-invokes per attempt and would read zero between attempts.
     */
    @PreDestroy
    void shutdown() {
        claimingEnabled = false;

        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        while (inFlightCount.get() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(DRAIN_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = inFlightCount.get();
        if (remaining > 0) {
            LOG.warnf("Shutdown timeout (%s) reached with %d notification(s) still in flight",
                    shutdownTimeout, remaining);
        }

        dispatchExecutor.shutdown();
    }

    private void dispatch(NotificationRepository.ClaimResult result) {
        try {
            concurrencySlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Notification notification = toNotification(result.notification());
        // Incremented here, before submit() - not inside the submitted task - so
        // shutdown()'s drain check can never observe a task that's been handed to
        // the executor but not yet counted.
        inFlightCount.incrementAndGet();
        dispatchExecutor.submit(() -> {
            try {
                notificationProcessor.processNotification(notification);
            } catch (Exception e) {
                LOG.errorf(e, "Unhandled error dispatching notification %s", notification.getId());
            } finally {
                inFlightCount.decrementAndGet();
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
