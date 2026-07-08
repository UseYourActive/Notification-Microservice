package bg.sit_varna.sit.si.service.redis;

import bg.sit_varna.sit.si.config.redis.RedisConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class DeduplicationService {

    private static final Logger LOG = Logger.getLogger(DeduplicationService.class);

    private final ValueCommands<String, String> valueCommands;
    private final KeyCommands<String> keyCommands;
    private final RedisConfig redisConfig;

    @Inject
    public DeduplicationService(RedisDataSource dataSource, RedisConfig redisConfig) {
        this.valueCommands = dataSource.value(String.class);
        this.keyCommands = dataSource.key();
        this.redisConfig = redisConfig;
    }

    public boolean isDuplicate(String recipient, NotificationChannel channel, String content) {
        if (!redisConfig.deduplication().enabled()) {
            return false;
        }

        try {
            String key = buildDeduplicationKey(recipient, channel, content);

            // Check if key exists (notification was recently sent)
            String existing = valueCommands.get(key);

            if (existing != null) {
                LOG.warnf("Duplicate notification detected for %s via %s", recipient, channel);
                return true;
            }

            // Not a duplicate - mark as sent
            valueCommands.set(key, "sent");
            keyCommands.expire(key, redisConfig.deduplication().ttl());

            LOG.debugf("Notification marked as sent: %s via %s", recipient, channel);
            return false;

        } catch (Exception e) {
            LOG.warnf(e, "Error checking deduplication (allowing notification)");
            return false;
        }
    }

    /**
     * Send-time guard, keyed by notification ID rather than content hash. Checked
     * immediately before the actual channel-strategy send(); markSent() must only be
     * called after send() returns successfully, never before, so a failed attempt
     * remains retryable. This is a second, narrower guard than isDuplicate() (which
     * only runs once at intake) - it protects against the same notification being
     * sent twice for real when a reaper reclaims a row that wasn't actually dead.
     */
    public boolean isAlreadySent(String notificationId) {
        if (!redisConfig.deduplication().enabled()) {
            return false;
        }
        try {
            return valueCommands.get(buildSendGuardKey(notificationId)) != null;
        } catch (Exception e) {
            LOG.warnf(e, "Error checking send-once guard (allowing send)");
            return false;
        }
    }

    public void markSent(String notificationId) {
        if (!redisConfig.deduplication().enabled()) {
            return;
        }
        try {
            String key = buildSendGuardKey(notificationId);
            valueCommands.set(key, "sent");
            keyCommands.expire(key, redisConfig.deduplication().ttl());
        } catch (Exception e) {
            LOG.warnf(e, "Error marking send-once guard");
        }
    }

    private String buildSendGuardKey(String notificationId) {
        return "dedup:send-guard:" + notificationId;
    }

    private String buildDeduplicationKey(String recipient, NotificationChannel channel, String content) {
        String combined = recipient + ":" + channel.name() + ":" + content;
        String hash = hashContent(combined);
        return "dedup:" + hash;
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("SHA-256 algorithm not available", e);
            return String.valueOf(content.hashCode());
        }
    }
}
