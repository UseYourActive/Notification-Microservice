package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Micrometer metrics for the durable notification queue - distinct from the
 * existing (Redis-counter-based) MetricsService, which predates this convention.
 */
@ApplicationScoped
public class QueueMetricsService {

    private final NotificationRepository notificationRepository;
    private final Counter claimedCounter;
    private final Counter reapedCounter;
    private final Counter poisonedCounter;

    @Inject
    public QueueMetricsService(MeterRegistry registry, NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;

        this.claimedCounter = Counter.builder("notifications.queue.claimed.total")
                .description("Rows claimed from the durable notification queue")
                .register(registry);
        this.reapedCounter = Counter.builder("notifications.queue.reaped.total")
                .description("Rows reclaimed from PROCESSING after the visibility timeout expired")
                .register(registry);
        this.poisonedCounter = Counter.builder("notifications.queue.poisoned.total")
                .description("Notifications that exhausted their cold-retry cycles and were left terminally FAILED")
                .register(registry);

        Gauge.builder("notifications.queue.depth", notificationRepository,
                        repo -> repo.countByStatus(NotificationStatus.QUEUED))
                .description("Number of notifications currently QUEUED")
                .register(registry);
        Gauge.builder("notifications.queue.oldest.age.seconds", this, QueueMetricsService::oldestQueuedAgeSeconds)
                .description("Age in seconds of the oldest QUEUED notification, 0 when the queue is empty")
                .register(registry);
    }

    public void recordClaimed(int count) {
        if (count > 0) {
            claimedCounter.increment(count);
        }
    }

    public void recordReaped(int count) {
        if (count > 0) {
            reapedCounter.increment(count);
        }
    }

    public void recordPoisoned() {
        poisonedCounter.increment();
    }

    private double oldestQueuedAgeSeconds() {
        return notificationRepository.findOldestQueuedCreatedAt()
                .map(createdAt -> Duration.between(createdAt, LocalDateTime.now()).getSeconds())
                .map(Long::doubleValue)
                .orElse(0.0);
    }
}
