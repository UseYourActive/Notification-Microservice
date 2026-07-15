package bg.sit_varna.sit.si.exception.mapper;

import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Shared response-building for the two deserialization-failure mappers
 * ({@link JsonbDeserializationExceptionMapper}, {@link JacksonDeserializationExceptionMapper}) -
 * same 400 VALIDATION_FAILED shape as {@link ConstraintViolationExceptionMapper}.
 */
final class DeserializationErrorTranslator {

    private DeserializationErrorTranslator() {
    }

    static Response toValidationFailedResponse(String detail) {
        ErrorResponse error = ErrorResponse.of("VALIDATION_FAILED", detail, List.of(detail));
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
}
