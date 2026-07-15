package bg.sit_varna.sit.si.qacommons;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.dto.response.SendNotificationResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service running externally - see root
 * README. Run with {@code mvn test -DrunLive=true}.
 *
 * <p>Documents *current* behavior only - two identical sends are accepted
 * independently, each with its own id. There is no deduplication/
 * idempotency on this endpoint today. If that ever ships, this test starts
 * failing - that's the intended alarm, not a bug in the test.
 */
@Tag("live")
class DuplicateSendLiveTest {

    @Test
    void send_identicalPayloadTwice_acceptsBothIndependently() {
        // Arrange
        NotificationsEndpoint endpoint = new NotificationsEndpoint(QaConfig.fromEnv());
        String recipient = "live-suite-dup-" + UUID.randomUUID() + "@example.com";
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, recipient, null, "qa-commons-live-suite duplicate send", null);

        // Act
        ApiResult<SendNotificationResponse, ErrorResponse> first = endpoint.send(request);
        ApiResult<SendNotificationResponse, ErrorResponse> second = endpoint.send(request);

        // Assert
        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        SendNotificationResponse firstResponse = first.expectSuccess();
        SendNotificationResponse secondResponse = second.expectSuccess();
        assertThat(firstResponse.notificationId()).isNotEqualTo(secondResponse.notificationId());
    }
}
