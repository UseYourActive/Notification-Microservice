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
 */
@Tag("live")
class SendNotificationLiveTest {

    private NotificationsEndpoint endpoint() {
        return new NotificationsEndpoint(QaConfig.fromEnv());
    }

    @Test
    void send_validRequest_returns202WithMatchingShape() {
        // Arrange
        String recipient = "live-suite-" + UUID.randomUUID() + "@example.com";
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, recipient, null, "qa-commons-live-suite positive send", null);

        // Act
        ApiResult<SendNotificationResponse, ErrorResponse> result = endpoint().send(request);

        // Assert
        assertThat(result.status()).isEqualTo(202);
        SendNotificationResponse response = result.expectSuccess();
        assertThat(response.notificationId()).isNotBlank();
        assertThat(response.recipient()).isEqualTo(recipient);
        assertThat(response.channel()).isEqualTo("EMAIL");
        assertThat(response.status()).isNotNull();
    }

    @Test
    void send_missingRecipient_returns400ValidationFailed() {
        // Arrange
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, "", null, "qa-commons-live-suite validation-negative send", null);

        // Act
        ApiResult<SendNotificationResponse, ErrorResponse> result = endpoint().send(request);

        // Assert
        assertThat(result.status()).isEqualTo(400);
        ErrorResponse error = result.expectFailure();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
    }
}
