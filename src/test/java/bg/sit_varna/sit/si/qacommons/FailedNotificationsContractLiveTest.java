package bg.sit_varna.sit.si.qacommons;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.dto.response.FailedNotificationResponse;
import bg.sit_varna.sit.si.dto.response.PageResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service running externally - see root
 * README. Run with {@code mvn test -DrunLive=true}.
 */
@Tag("live")
class FailedNotificationsContractLiveTest {

    @Test
    void listFailed_returnsPagedEnvelopeContractShape() {
        // Arrange
        FailedNotificationsEndpoint endpoint = new FailedNotificationsEndpoint(QaConfig.fromEnv());

        // Act
        ApiResult<PageResponse<FailedNotificationResponse>, ErrorResponse> result = endpoint.list(0, 20);

        // Assert - envelope shape only, tolerating an empty items list.
        assertThat(result.status()).isEqualTo(200);
        PageResponse<FailedNotificationResponse> page = result.expectSuccess();
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalItems()).isGreaterThanOrEqualTo(0);
        assertThat(page.totalPages()).isGreaterThanOrEqualTo(0);
        assertThat(page.items()).isNotNull();
        page.items().forEach(item -> {
            assertThat(item.notificationId()).isNotBlank();
            assertThat(item.recipient()).isNotBlank();
            assertThat(item.channel()).isNotBlank();
            assertThat(item.status()).isNotNull();
            assertThat(item.createdAt()).isNotNull();
            assertThat(item.updatedAt()).isNotNull();
        });
    }
}
