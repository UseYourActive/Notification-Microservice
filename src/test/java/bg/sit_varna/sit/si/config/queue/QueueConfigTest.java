package bg.sit_varna.sit.si.config.queue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@QuarkusTest
public class QueueConfigTest {

    @Inject
    QueueConfig queueConfig;

    @Test
    void loadsConfiguredDefaults() {
        Assertions.assertEquals(Duration.ofSeconds(1), queueConfig.pollInterval());
        Assertions.assertEquals(20, queueConfig.batchSize());
        Assertions.assertEquals(Duration.ofSeconds(60), queueConfig.visibilityTimeout());
        Assertions.assertEquals(5, queueConfig.maxColdRetryCycles());
        Assertions.assertEquals(20, queueConfig.workerConcurrency());
    }
}
