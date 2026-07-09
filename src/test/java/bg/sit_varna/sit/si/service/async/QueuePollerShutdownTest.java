package bg.sit_varna.sit.si.service.async;

import bg.sit_varna.sit.si.config.queue.QueueConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.mapper.NotificationRecordMapper;
import bg.sit_varna.sit.si.repository.ClaimResult;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
 *
 * In-flight tracking lives in QueuePoller itself (incremented synchronously in
 * dispatch(), before the work is handed to the executor), so poll() must actually
 * run to populate it - there's no longer a getInFlightCount() to stub directly.
 */
public class QueuePollerShutdownTest {

    private NotificationRepository notificationRepository;
    private NotificationProcessor notificationProcessor;
    private QueueConfig queueConfig;
    private QueueMetricsService queueMetricsService;
    private NotificationRecordMapper notificationRecordMapper;

    @BeforeEach
    void setUp() {
        notificationRepository = Mockito.mock(NotificationRepository.class);
        notificationProcessor = Mockito.mock(NotificationProcessor.class);
        queueConfig = Mockito.mock(QueueConfig.class);
        queueMetricsService = Mockito.mock(QueueMetricsService.class);
        notificationRecordMapper = new NotificationRecordMapper();
        Mockito.when(queueConfig.workerConcurrency()).thenReturn(5);
        Mockito.when(queueConfig.batchSize()).thenReturn(10);
        Mockito.when(queueConfig.visibilityTimeout()).thenReturn(Duration.ofSeconds(60));
    }

    @Test
    void shutdownReturnsAsSoonAsInFlightWorkDrains() throws Exception {
        Duration processingTime = Duration.ofMillis(300);
        Mockito.doAnswer(invocation -> {
                    Thread.sleep(processingTime.toMillis());
                    return null;
                }).when(notificationProcessor).processNotification(any());
        Mockito.when(notificationRepository.claimBatch(anyInt(), anyString(), anyLong()))
                .thenReturn(List.of(claimResult()));

        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, Duration.ofSeconds(5), queueMetricsService, notificationRecordMapper);

        poller.poll(); // synchronously increments in-flight, then hands the slow work to a virtual thread

        long start = System.nanoTime();
        poller.shutdown();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMs >= processingTime.toMillis() - 50,
                "shutdown() returned before the in-flight work actually finished (took " + elapsedMs + "ms)");
        assertTrue(elapsedMs < Duration.ofSeconds(2).toMillis(),
                "shutdown() should return once in-flight work drains, not wait out the full timeout (took "
                        + elapsedMs + "ms)");
    }

    @Test
    void shutdownRespectsTimeoutWhenWorkNeverDrains() throws Exception {
        Mockito.doAnswer(invocation -> {
                    Thread.sleep(Duration.ofSeconds(2).toMillis()); // outlives the configured timeout below
                    return null;
                }).when(notificationProcessor).processNotification(any());
        Mockito.when(notificationRepository.claimBatch(anyInt(), anyString(), anyLong()))
                .thenReturn(List.of(claimResult()));

        Duration timeout = Duration.ofMillis(300);
        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, timeout, queueMetricsService, notificationRecordMapper);

        poller.poll();

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
        QueuePoller poller = new QueuePoller(notificationRepository, notificationProcessor,
                queueConfig, Duration.ofSeconds(5), queueMetricsService, notificationRecordMapper);

        poller.shutdown();
        poller.poll();

        Mockito.verify(notificationRepository, Mockito.never())
                .claimBatch(anyInt(), anyString(), anyLong());
    }

    private static ClaimResult claimResult() {
        NotificationRecord record = new NotificationRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRecipient("shutdown-test@example.com");
        record.setChannel(NotificationChannel.EMAIL);
        record.setStatus(NotificationStatus.PROCESSING);
        record.setMessage("Hello");
        record.setLocale("en");
        return new ClaimResult(record, false);
    }
}
