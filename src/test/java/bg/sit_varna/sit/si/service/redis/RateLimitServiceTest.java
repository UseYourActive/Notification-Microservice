package bg.sit_varna.sit.si.service.redis;

import bg.sit_varna.sit.si.config.redis.RedisConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, Long> valueCommands = mock(ValueCommands.class);

    @SuppressWarnings("unchecked")
    private final KeyCommands<String> keyCommands = mock(KeyCommands.class);

    private final RedisDataSource dataSource = mock(RedisDataSource.class);
    private final RedisConfig redisConfig = mock(RedisConfig.class);
    private final RedisConfig.RateLimitConfig rateLimitConfig = mock(RedisConfig.RateLimitConfig.class);

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(dataSource.value(Long.class)).thenReturn(valueCommands);
        when(dataSource.key()).thenReturn(keyCommands);
        when(redisConfig.rateLimit()).thenReturn(rateLimitConfig);

        rateLimitService = new RateLimitService(dataSource, redisConfig);
    }

    @Test
    void isAllowed_rateLimitDisabled_returnsTrueWithoutTouchingRedis() {
        when(rateLimitConfig.enabled()).thenReturn(false);

        boolean allowed = rateLimitService.isAllowed("someone@example.com", NotificationChannel.EMAIL);

        assertTrue(allowed);
        verify(valueCommands, never()).incr(anyString());
        verify(keyCommands, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void isAllowed_firstRequest_initializesExpiryAndAllows() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(rateLimitConfig.emailMax()).thenReturn(10);
        when(rateLimitConfig.emailWindow()).thenReturn(Duration.ofHours(1));
        when(valueCommands.incr(anyString())).thenReturn(1L);

        boolean allowed = rateLimitService.isAllowed("someone@example.com", NotificationChannel.EMAIL);

        assertTrue(allowed);
        verify(keyCommands).expire(anyString(), any(Duration.class));
    }

    @Test
    void isAllowed_subsequentRequestWithinLimit_doesNotReinitializeExpiry() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(rateLimitConfig.emailMax()).thenReturn(10);
        when(rateLimitConfig.emailWindow()).thenReturn(Duration.ofHours(1));
        when(valueCommands.incr(anyString())).thenReturn(2L);

        boolean allowed = rateLimitService.isAllowed("someone@example.com", NotificationChannel.EMAIL);

        assertTrue(allowed);
        verify(keyCommands, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void isAllowed_countExceedsMax_returnsFalse() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(rateLimitConfig.emailMax()).thenReturn(10);
        when(rateLimitConfig.emailWindow()).thenReturn(Duration.ofHours(1));
        when(valueCommands.incr(anyString())).thenReturn(11L);

        boolean allowed = rateLimitService.isAllowed("someone@example.com", NotificationChannel.EMAIL);

        assertFalse(allowed);
    }

    @Test
    void isAllowed_redisThrowsOnIncrement_failsOpenAndReturnsTrue() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(valueCommands.incr(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        boolean allowed = rateLimitService.isAllowed("someone@example.com", NotificationChannel.EMAIL);

        assertTrue(allowed);
    }

    @ParameterizedTest
    @CsvSource({
            "SMS, 5",
            "TELEGRAM, 20"
    })
    void isAllowed_perChannelMaxRequests_appliedCorrectly(NotificationChannel channel, int max) {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(rateLimitConfig.smsMax()).thenReturn(5);
        when(rateLimitConfig.smsWindow()).thenReturn(Duration.ofHours(1));
        when(rateLimitConfig.telegramMax()).thenReturn(20);
        when(rateLimitConfig.telegramWindow()).thenReturn(Duration.ofHours(1));

        when(valueCommands.incr(anyString())).thenReturn((long) (max + 1));

        boolean allowed = rateLimitService.isAllowed("recipient", channel);

        assertFalse(allowed);
    }

    @ParameterizedTest
    @EnumSource(NotificationChannel.class)
    void isAllowed_perChannelWindow_expiresWithConfiguredWindowOnFirstRequest(NotificationChannel channel) {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(rateLimitConfig.emailMax()).thenReturn(10);
        when(rateLimitConfig.emailWindow()).thenReturn(Duration.ofMinutes(1));
        when(rateLimitConfig.smsMax()).thenReturn(5);
        when(rateLimitConfig.smsWindow()).thenReturn(Duration.ofMinutes(2));
        when(rateLimitConfig.telegramMax()).thenReturn(20);
        when(rateLimitConfig.telegramWindow()).thenReturn(Duration.ofMinutes(3));
        when(valueCommands.incr(anyString())).thenReturn(1L);

        rateLimitService.isAllowed("recipient", channel);

        Duration expectedWindow = switch (channel) {
            case EMAIL -> Duration.ofMinutes(1);
            case SMS -> Duration.ofMinutes(2);
            case TELEGRAM -> Duration.ofMinutes(3);
        };
        verify(keyCommands).expire(anyString(), eq(expectedWindow));
    }

    @Test
    void getResetTime_rateLimitDisabled_returnsZeroWithoutTouchingRedis() {
        when(rateLimitConfig.enabled()).thenReturn(false);

        long resetTime = rateLimitService.getResetTime("someone@example.com", NotificationChannel.EMAIL);

        assertEquals(0L, resetTime);
        verify(keyCommands, never()).ttl(anyString());
    }

    @Test
    void getResetTime_positiveTtl_returnsTtlAsIs() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(keyCommands.ttl(anyString())).thenReturn(42L);

        long resetTime = rateLimitService.getResetTime("someone@example.com", NotificationChannel.EMAIL);

        assertEquals(42L, resetTime);
    }

    @ParameterizedTest
    @CsvSource({ "0", "-1", "-2" })
    void getResetTime_nonPositiveTtl_collapsesToZero(long ttl) {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(keyCommands.ttl(anyString())).thenReturn(ttl);

        long resetTime = rateLimitService.getResetTime("someone@example.com", NotificationChannel.EMAIL);

        assertEquals(0L, resetTime);
    }

    @Test
    void getResetTime_redisThrowsOnTtlLookup_failsOpenAndReturnsZero() {
        when(rateLimitConfig.enabled()).thenReturn(true);
        when(keyCommands.ttl(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        long resetTime = rateLimitService.getResetTime("someone@example.com", NotificationChannel.EMAIL);

        assertEquals(0L, resetTime);
    }
}
