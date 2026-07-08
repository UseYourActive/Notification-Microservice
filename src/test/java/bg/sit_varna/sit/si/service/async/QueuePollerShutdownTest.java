package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Plain unit test (no @QuarkusTest) - QueuePoller is @ApplicationScoped, and calling
 * its real shutdown() against the shared test-suite app instance would permanently
 * disable claiming for every test that runs afterward in the same JVM, since
 * claimingEnabled/dispatchExecutor have no way to reset. Constructing it directly
 * with mocks keeps this test isolated and lets timing be controlled precisely
 * instead of relying on real Thread.sleep() delays.
 */
public class QueuePollerShutdownTest {

    private NotificationRepository notificationRepository;
    private NotificationProcessor notificationProcessor;
    private QueueConfig queueConfig;

    @BeforeEach
    void setUp() {
        notificationRepository = Mockito.mock(NotificationRepository.class);
        notificationProcessor = Mockito.mock(NotificationProcessor.class);
        queueConfig = Mockito.mock(QueueConfig.class);
        Mockito.when(queueConfig.workerConcurrency()).thenReturn(5);
        Mockito.when(queueConfig.batchSize()).thenReturn(10);
        Mockito.when(queueConfig.visibilityTimeout()).thenReturn(Duration.ofSeconds(60));
    }

    @Test
    void shutdownReturnsAsSoonAsInFlightWorkDrains() {
        // 3 checks see in-flight work, the 4th sees zero - simulates draining
        // shortly after shutdown starts, well before the configured timeout.
        Mockito.when(notificationProcessor.getInFlightCount()).thenReturn(1, 1, 1, 0);

        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, Duration.ofSeconds(5));

        long start = System.nanoTime();
        poller.shutdown();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMs < Duration.ofSeconds(2).toMillis(),
                "shutdown() should return once in-flight work drains, not wait out the full timeout (took "
                        + elapsedMs + "ms)");
    }

    @Test
    void shutdownRespectsTimeoutWhenWorkNeverDrains() {
        Mockito.when(notificationProcessor.getInFlightCount()).thenReturn(1); // never drains

        Duration timeout = Duration.ofMillis(300);
        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, timeout);

        long start = System.nanoTime();
        poller.shutdown();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMs >= timeout.toMillis(),
                "shutdown() must wait at least the configured timeout when work never drains (took "
                        + elapsedMs + "ms)");
        assertTrue(elapsedMs < timeout.toMillis() + 1000,
                "shutdown() must not wait dramatically longer than the configured timeout (took "
                        + elapsedMs + "ms)");
    }

    @Test
    void shutdownStopsClaimingNewBatches() {
        Mockito.when(notificationProcessor.getInFlightCount()).thenReturn(0);

        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, Duration.ofSeconds(5));

        poller.shutdown();
        poller.poll();

        Mockito.verify(notificationRepository, Mockito.never())
                .claimBatch(anyInt(), anyString(), anyLong());
    }
}
