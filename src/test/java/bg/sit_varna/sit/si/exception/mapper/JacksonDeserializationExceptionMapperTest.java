package bg.sit_varna.sit.si.exception.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * Unit test, not an integration test - this app's JSON bodies currently
 * route through JSON-B, not Jackson (see
 * {@link JsonbDeserializationExceptionMapper}), so this path can't be
 * reached via a real request today. Proves the mapper's logic directly so
 * it's correct whenever that reader race resolves differently.
 */
class JacksonDeserializationExceptionMapperTest {

    private final JacksonDeserializationExceptionMapper mapper = new JacksonDeserializationExceptionMapper();

    @Test
    void toResponse_invalidEnumValue_returns400WithFieldNameAndAcceptedValues() {
        // Arrange
        InvalidFormatException exception = InvalidFormatException.from(
                null, "not a valid value", "CARRIER_PIGEON", NotificationChannel.class);
        exception.prependPath(Object.class, "channel");

        // Act
        Response response = mapper.toResponse(exception);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.message()).contains("channel").contains("CARRIER_PIGEON");
        assertThat(error.details()).anyMatch(d -> d.contains("EMAIL") && d.contains("SMS") && d.contains("TELEGRAM"));
    }

    @Test
    void toResponse_nonEnumMismatch_returns400WithoutAcceptedValues() {
        // Arrange
        InvalidFormatException exception = InvalidFormatException.from(
                null, "not a valid value", "not-a-number", Integer.class);
        exception.prependPath(Object.class, "someField");

        // Act
        Response response = mapper.toResponse(exception);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.message()).contains("someField").contains("not-a-number");
    }
}
