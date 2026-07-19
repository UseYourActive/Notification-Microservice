package bg.sit_varna.sit.si.qacommons;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.json.JsonMapperFactory;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service running externally - see root
 * README. Run with {@code mvn test -DrunLive=true}.
 *
 * <p>{@code NotificationChannel} is a closed Java enum, so a syntactically
 * invalid channel value still can't be expressed through the typed
 * {@code NotificationsEndpoint}/{@code SendNotificationRequest} contract -
 * this test still goes around it with a raw JSON body. Was
 * {@code InvalidChannelFindingLiveTest}, pinning a known 500 bug (see
 * docs/specs/qa-commons-live-suite/findings.md #1); became the acceptance
 * check for that fix (docs/specs/api-findings-fixes), asserting the 400
 * VALIDATION_FAILED behavior instead, typed via {@link ApiResult#expectFailure()}.
 *
 * <p>Now also the live acceptance check for finding #4
 * (docs/specs/json-stack-consolidation): with quarkus-rest-jsonb removed,
 * this request is served by {@code JacksonDeserializationExceptionMapper}
 * instead of the JSON-B path, and {@code InvalidFormatException.getPath()}
 * recovers the literal JSON field name - the message-asymmetry improvement
 * predicted in api-findings-fixes/plan.md. Asserted live, not just observed.
 */
@Tag("live")
class InvalidChannelValidationLiveTest {

    @Test
    void send_unrecognizedChannelValue_returns400ValidationFailed() throws Exception {
        // Arrange
        String rawBody = """
                {"channel":"CARRIER_PIGEON","recipient":"someone@example.com","message":"validation-check"}""";

        // Act
        Response response = given()
                .baseUri(QaConfig.fromEnv().baseUrl())
                .contentType(ContentType.JSON)
                .body(rawBody)
                .when()
                .post("/api/v1/notifications/send");
        ObjectMapper mapper = JsonMapperFactory.newMapper();
        ErrorResponse errorBody = mapper.readValue(response.body().asString(), ErrorResponse.class);
        ApiResult<Void, ErrorResponse> result = new ApiResult.Failure<>(response.statusCode(), Map.of(), errorBody);

        // Assert
        assertThat(result.status()).isEqualTo(400);
        ErrorResponse error = result.expectFailure();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        // Literal JSON field name, not the enum type name - the
        // Jackson-path detail this DTO now always takes (finding #4).
        assertThat(error.message()).contains("field 'channel'");
    }
}
