package bg.sit_varna.sit.si.service.redis;

import bg.sit_varna.sit.si.dto.model.Notification;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.ScoreRange;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class RedisRetryService {

    private static final Logger LOG = Logger.getLogger(RedisRetryService.class);
    private static final String RETRY_KEY = "notifications:retry_queue";
    private final Clock clock;
    private final SortedSetCommands<String, Notification> zsetCommands;

    @Inject
    public RedisRetryService(RedisDataSource dataSource, Clock clock) {
        this.zsetCommands = dataSource.sortedSet(String.class, Notification.class);
        this.clock = clock;
    }

    public void scheduleRetry(Notification notification, long delaySeconds) {
        long executeAt = Instant.now(clock).getEpochSecond() + delaySeconds;

        zsetCommands.zadd(RETRY_KEY, executeAt, notification);

        LOG.infof("Scheduled retry for %s in %d seconds (Redis ZSET)",
                notification.getRecipient(), delaySeconds);
    }

    public List<Notification> fetchDueNotifications() {
        long now = Instant.now(clock).getEpochSecond();

        // Get everything due up to this exact second
        List<Notification> due = zsetCommands.zrangebyscore(RETRY_KEY, ScoreRange.from(Double.NEGATIVE_INFINITY, now));

        if (!due.isEmpty()) {
            // Remove them from Redis so we don't process them twice
            for (Notification n : due) {
                zsetCommands.zrem(RETRY_KEY, n);
            }
        }

        return due;
    }
}
