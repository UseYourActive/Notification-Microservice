package bg.sit_varna.sit.si.exception.mapper;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Handles request-body deserialization failures from the JSON-B (Yasson)
 * reader - today's actual {@code MessageBodyReader} for this app's JSON
 * bodies, per a reader-priority race with Jackson (also on the classpath;
 * see docs/specs/qa-commons-live-suite/findings.md #4). Sibling to
 * {@link JacksonDeserializationExceptionMapper}, kept correct in case that
 * race is ever resolved differently.
 */
@Provider
public class JsonbDeserializationExceptionMapper implements ExceptionMapper<JsonbException> {

    private static final Logger LOG = Logger.getLogger(JsonbDeserializationExceptionMapper.class);

    private static final Pattern NO_ENUM_CONSTANT = Pattern.compile("No enum constant ([\\w.$]+)\\.([^.]+)$");

    @Override
    public Response toResponse(JsonbException exception) {
        LOG.warnf("Request body deserialization failed (JSON-B): %s", exception.getMessage());

        String detail = enumDetail(exception)
                .orElseGet(() -> "Malformed request body: " + rootCauseMessage(exception));

        return DeserializationErrorTranslator.toValidationFailedResponse(detail);
    }

    // JsonbException/Yasson doesn't retain a JSON property path the way
    // Jackson's InvalidFormatException does - this names the enum type,
    // not the literal JSON field. Consolidating onto Jackson (findings.md
    // #4) would recover a field-path detail here for free.
    private static Optional<String> enumDetail(Throwable exception) {
        Throwable cause = rootCause(exception);
        Matcher matcher = NO_ENUM_CONSTANT.matcher(String.valueOf(cause.getMessage()));
        if (!matcher.find()) {
            return Optional.empty();
        }

        String enumClassName = matcher.group(1);
        String invalidValue = matcher.group(2);
        try {
            Class<?> enumType = Class.forName(enumClassName);
            if (!enumType.isEnum()) {
                return Optional.empty();
            }
            String acceptedValues = Arrays.stream(enumType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return Optional.of("Invalid value '%s' for %s; accepted values: %s"
                    .formatted(invalidValue, enumType.getSimpleName(), acceptedValues));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootCauseMessage(Throwable exception) {
        String message = rootCause(exception).getMessage();
        return message != null ? message : exception.getMessage();
    }
}
