package bg.sit_varna.sit.si.service.redis;

import bg.sit_varna.sit.si.config.redis.RedisConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeduplicationServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> valueCommands = mock(ValueCommands.class);

    @SuppressWarnings("unchecked")
    private final KeyCommands<String> keyCommands = mock(KeyCommands.class);

    private final RedisDataSource dataSource = mock(RedisDataSource.class);
    private final RedisConfig redisConfig = mock(RedisConfig.class);
    private final RedisConfig.DeduplicationConfig deduplicationConfig = mock(RedisConfig.DeduplicationConfig.class);

    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        when(dataSource.value(String.class)).thenReturn(valueCommands);
        when(dataSource.key()).thenReturn(keyCommands);
        when(redisConfig.deduplication()).thenReturn(deduplicationConfig);

        deduplicationService = new DeduplicationService(dataSource, redisConfig);
    }

    // ------------------------------------------------------------------
    // Fail-open behavior when Redis throws (deterministic "Redis is down")
    // ------------------------------------------------------------------

    @Test
    void isDuplicate_redisThrowsOnGet_failsOpenAndReturnsFalse() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertFalse(duplicate);
    }

    @Test
    void isDuplicate_redisThrowsOnSet_failsOpenAndReturnsFalse() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("Redis unavailable")).when(valueCommands).set(anyString(), anyString());

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertFalse(duplicate);
    }

    @Test
    void isDuplicate_redisThrowsOnExpire_failsOpenAndReturnsFalse() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenReturn(null);
        when(keyCommands.expire(anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertFalse(duplicate);
    }

    @Test
    void isAlreadySent_redisThrowsOnGet_failsOpenAndReturnsFalse() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        boolean alreadySent = deduplicationService.isAlreadySent("notification-1");

        assertFalse(alreadySent);
    }

    @Test
    void markSent_redisThrowsOnSet_doesNotPropagateException() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        doThrow(new RuntimeException("Redis unavailable")).when(valueCommands).set(anyString(), anyString());

        assertDoesNotThrow(() -> deduplicationService.markSent("notification-1"));
    }

    @Test
    void markSent_redisThrowsOnExpire_doesNotPropagateException() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(keyCommands.expire(anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        assertDoesNotThrow(() -> deduplicationService.markSent("notification-1"));
    }

    // ------------------------------------------------------------------
    // Disabled short-circuit: fallback returned before touching Redis at all
    // ------------------------------------------------------------------

    @Test
    void isDuplicate_deduplicationDisabled_returnsFalseWithoutTouchingRedis() {
        when(deduplicationConfig.enabled()).thenReturn(false);

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertFalse(duplicate);
        verifyNoInteractions(valueCommands);
        verifyNoInteractions(keyCommands);
    }

    @Test
    void isAlreadySent_deduplicationDisabled_returnsFalseWithoutTouchingRedis() {
        when(deduplicationConfig.enabled()).thenReturn(false);

        boolean alreadySent = deduplicationService.isAlreadySent("notification-1");

        assertFalse(alreadySent);
        verifyNoInteractions(valueCommands);
        verifyNoInteractions(keyCommands);
    }

    @Test
    void markSent_deduplicationDisabled_isNoOpWithoutTouchingRedis() {
        when(deduplicationConfig.enabled()).thenReturn(false);

        assertDoesNotThrow(() -> deduplicationService.markSent("notification-1"));

        verifyNoInteractions(valueCommands);
        verifyNoInteractions(keyCommands);
    }

    // ------------------------------------------------------------------
    // Normal enabled-path behavior
    // ------------------------------------------------------------------

    @Test
    void isDuplicate_existingKeyFound_returnsTrueAndDoesNotRewriteKey() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenReturn("sent");

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertTrue(duplicate);
        verify(valueCommands, never()).set(anyString(), anyString());
        verify(keyCommands, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void isDuplicate_noExistingKey_returnsFalseAndMarksAsSent() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(deduplicationConfig.ttl()).thenReturn(Duration.ofMinutes(5));
        when(valueCommands.get(anyString())).thenReturn(null);

        boolean duplicate = deduplicationService.isDuplicate("someone@example.com", NotificationChannel.EMAIL, "content");

        assertFalse(duplicate);
        verify(valueCommands).set(anyString(), eq("sent"));
        verify(keyCommands).expire(anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void isAlreadySent_keyExists_returnsTrue() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenReturn("sent");

        boolean alreadySent = deduplicationService.isAlreadySent("notification-1");

        assertTrue(alreadySent);
    }

    @Test
    void isAlreadySent_keyAbsent_returnsFalse() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(valueCommands.get(anyString())).thenReturn(null);

        boolean alreadySent = deduplicationService.isAlreadySent("notification-1");

        assertFalse(alreadySent);
    }

    @Test
    void markSent_deduplicationEnabled_setsKeyAndAppliesConfiguredTtl() {
        when(deduplicationConfig.enabled()).thenReturn(true);
        when(deduplicationConfig.ttl()).thenReturn(Duration.ofMinutes(5));

        deduplicationService.markSent("notification-1");

        verify(valueCommands).set(anyString(), eq("sent"));
        verify(keyCommands).expire(anyString(), eq(Duration.ofMinutes(5)));
    }
}
