package bg.sit_varna.sit.si.config.channel;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "telegram")
public interface TelegramConfig {

    @WithName("enabled")
    @WithDefault("false")
    boolean enabled();

    @WithName("bot.token")
    @WithDefault("")
    String botToken();

    @WithName("api.base-url")
    @WithDefault("https://api.telegram.org/bot")
    String apiBaseUrl();

    @WithName("bot.username")
    @WithDefault("")
    String botUsername();

    @WithName("default-parse-mode")
    @WithDefault("HTML")
    String defaultParseMode();

    @WithName("disable-web-page-preview")
    @WithDefault("false")
    boolean disableWebPagePreview();

    @WithName("allow-sending-without-reply")
    @WithDefault("true")
    boolean allowSendingWithoutReply();

    default boolean isConfigured() {
        return enabled() && botToken() != null && !botToken().isEmpty();
    }
}
