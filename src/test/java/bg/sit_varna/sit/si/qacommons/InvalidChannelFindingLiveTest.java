package bg.sit_varna.sit.si.qacommons;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.qacommons.core.config.QaConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service running externally - see root
 * README. Run with {@code mvn test -DrunLive=true}.
 *
 * <p>{@code NotificationChannel} is a closed Java enum, so a syntactically
 * invalid channel value can't be expressed through the typed
 * {@code NotificationsEndpoint}/{@code SendNotificationRequest} contract -
 * this test deliberately goes around it with a raw JSON body to pin a known
 * finding: no exception mapper in {@code exception/mapper/} catches a
 * Jackson deserialization failure, so an unrecognized {@code channel} value
 * currently surfaces as an unhandled 500, not a 400. See
 * docs/specs/qa-commons-live-suite/findings.md. This test asserts *current*
 * behavior on purpose - it is never to be weakened to expect 400, and the
 * gap is never fixed in this branch.
 */
@Tag("live")
class InvalidChannelFindingLiveTest {

    @Test
    void send_unrecognizedChannelValue_currentlyReturns500_knownFinding() {
        // Arrange
        String rawBody = """
                {"channel":"CARRIER_PIGEON","recipient":"someone@example.com","message":"finding-pin"}""";

        // Act
        Response response = given()
                .baseUri(QaConfig.fromEnv().baseUrl())
                .contentType(ContentType.JSON)
                .body(rawBody)
                .when()
                .post("/api/v1/notifications/send");

        // Assert
        assertThat(response.statusCode()).isEqualTo(500);
    }
}
