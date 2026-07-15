package bg.sit_varna.sit.si.exception.mapper;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Handles request-body deserialization failures from the Jackson reader.
 * Dormant today - this app's JSON bodies currently route through JSON-B
 * (see {@link JsonbDeserializationExceptionMapper}), a reader-priority race
 * documented in docs/specs/qa-commons-live-suite/findings.md #4 - but kept
 * correct and tested so a future change in that race (or a consolidation
 * onto Jackson) doesn't silently reopen the 500. Unlike the JSON-B path,
 * {@link InvalidFormatException#getPath()} gives a real JSON field name.
 */
@Provider
public class JacksonDeserializationExceptionMapper implements ExceptionMapper<InvalidFormatException> {

    private static final Logger LOG = Logger.getLogger(JacksonDeserializationExceptionMapper.class);

    @Override
    public Response toResponse(InvalidFormatException exception) {
        LOG.warnf("Request body deserialization failed (Jackson): %s", exception.getMessage());

        String fieldName = fieldName(exception);
        Class<?> targetType = exception.getTargetType();
        String detail = (targetType != null && targetType.isEnum())
                ? "Invalid value '%s' for field '%s'; accepted values: %s"
                        .formatted(exception.getValue(), fieldName, acceptedValues(targetType))
                : "Invalid value '%s' for field '%s'".formatted(exception.getValue(), fieldName);

        return DeserializationErrorTranslator.toValidationFailedResponse(detail);
    }

    private static String acceptedValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    private static String fieldName(InvalidFormatException exception) {
        List<JsonMappingException.Reference> path = exception.getPath();
        if (path.isEmpty()) {
            return "<unknown>";
        }
        return path.get(path.size() - 1).getFieldName();
    }
}
