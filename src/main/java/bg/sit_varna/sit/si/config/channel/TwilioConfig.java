package bg.sit_varna.sit.si.config.channel;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "twilio")
public interface TwilioConfig {

    @WithName("account.sid")
    @WithDefault("")
    String accountSid();

    @WithName("auth.token")
    @WithDefault("")
    String authToken();

    @WithName("phone.number")
    @WithDefault("")
    String phoneNumber();

    @WithName("api.base-url")
    @WithDefault("https://api.twilio.com")
    String apiBaseUrl();

    default boolean isConfigured() {
        return !accountSid().isEmpty() &&
                !authToken().isEmpty() &&
                !phoneNumber().isEmpty();
    }
}
