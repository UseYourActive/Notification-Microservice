package bg.sit_varna.sit.si.config.channel;

import io.smallrye.config.ConfigMapping;
import jakarta.validation.constraints.NotBlank;

import java.util.Optional;

@ConfigMapping(prefix = "sendgrid")
public interface SendGridConfig {

    @NotBlank(message = "SendGrid API key is required")
    String apiKey();

    @NotBlank(message = "SendGrid from email is required")
    String fromEmail();

    Optional<String> webhookPublicKey();

    /**
     * Overrides the SendGrid API host for testing (format: "host[:port]", no scheme —
     * matches the SDK's own {@code Client#buildUri} contract). Unset in production.
     */
    Optional<String> apiBaseUrl();

    default String fromName() {
        return "Notification Service";
    }

    default boolean enabled() {
        return true;
    }

    default boolean isConfigured() {
        return enabled()
                && apiKey() != null && !apiKey().isBlank()
                && fromEmail() != null && !fromEmail().isBlank();
    }
}
