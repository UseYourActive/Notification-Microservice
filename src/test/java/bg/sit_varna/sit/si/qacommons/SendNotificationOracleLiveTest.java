package bg.sit_varna.sit.si.qacommons;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.dto.response.SendNotificationResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import bg.sit_varna.sit.si.qacommons.db.NotificationRow;
import bg.sit_varna.sit.si.qacommons.db.NotificationsOracle;
import bg.sit_varna.sit.si.qacommons.db.SchemaFingerprint;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.db.config.DbConfig;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service and its Postgres running
 * externally - see root README (this repo's compose stack remaps the DB
 * to host port 15432, so {@code QA_DB_PORT=15432}). Run with
 * {@code mvn test -DrunLive=true}.
 */
@Tag("live")
class SendNotificationOracleLiveTest {

    @Test
    void send_persistsRowWithMatchingIdentity() {
        // Arrange
        DbConfig dbConfig = DbConfig.fromEnv();
        SchemaFingerprint.verifyOnce(dbConfig);
        NotificationsEndpoint endpoint = new NotificationsEndpoint(QaConfig.fromEnv());
        NotificationsOracle oracle = new NotificationsOracle(dbConfig);
        String recipient = "live-suite-oracle-" + UUID.randomUUID() + "@example.com";
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, recipient, null, "qa-commons-live-suite oracle send", null);

        // Act
        ApiResult<SendNotificationResponse, ErrorResponse> result = endpoint.send(request);
        SendNotificationResponse sent = result.expectSuccess();
        Optional<NotificationRow> row = oracle.findById(sent.notificationId());

        // Assert - identity/existence only, never status/lock-value, since
        // the durable-queue poller can claim the row at any point after
        // intake (race, not a bug).
        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo(sent.notificationId());
        assertThat(row.get().recipient()).isEqualTo(recipient);
        assertThat(row.get().channel()).isEqualTo("EMAIL");
    }
}
