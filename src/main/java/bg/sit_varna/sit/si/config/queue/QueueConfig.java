package bg.sit_varna.sit.si.config.queue;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "queue")
public interface QueueConfig {

    @WithDefault("500ms")
    Duration pollInterval();

    @WithDefault("20")
    int batchSize();

    @WithDefault("60s")
    Duration visibilityTimeout();

    @WithDefault("5")
    int maxColdRetryCycles();

    @WithDefault("20")
    int workerConcurrency();
}
