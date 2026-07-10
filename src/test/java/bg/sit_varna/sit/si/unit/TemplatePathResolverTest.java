package bg.sit_varna.sit.si.unit;

import bg.sit_varna.sit.si.config.app.ApplicationConfig;
import bg.sit_varna.sit.si.template.processing.TemplatePathResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TemplatePathResolverTest {

    TemplatePathResolver resolver;
    ApplicationConfig config;

    @BeforeEach
    void setup() {
        config = Mockito.mock(ApplicationConfig.class);
        Mockito.when(config.defaultLocale()).thenReturn("en");
        resolver = new TemplatePathResolver(config);
    }

    @Test
    void testResolve_ValidInput() {
        String result = resolver.resolve("email/welcome", "bg");
        Assertions.assertEquals("email/welcome_bg.html", result);
    }

    @Test
    void testResolve_FallbackToDefaultLocale() {
        String result = resolver.resolve("email/welcome", null);
        Assertions.assertEquals("email/welcome_en.html", result);
    }

    @Test
    void testResolve_ThrowsOnInvalidInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolve(null, "en");
        });
    }

    @Test
    void testResolve_ThrowsOnBlankTemplateName() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolve("   ", "en");
        });
    }

    @Test
    void testResolve_FallbackToDefaultLocaleWhenLocaleBlank() {
        String result = resolver.resolve("email/welcome", "   ");
        Assertions.assertEquals("email/welcome_en.html", result);
    }

    @Test
    void testResolve_ThrowsOnMissingSlashSeparator() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolve("welcome", "en");
        });
        Assertions.assertEquals("Invalid template name format", exception.getMessage());
    }

    @Test
    void testResolve_FallsBackToHtmlExtensionForUnknownChannel() {
        String result = resolver.resolve("unknown/welcome", "en");
        Assertions.assertEquals("unknown/welcome_en.html", result);
    }

    @Test
    void testResolve_SmsChannelResolvesToTxtExtension() {
        String result = resolver.resolve("sms/otp", "en");
        Assertions.assertEquals("sms/otp_en.txt", result);
    }

    @Test
    void testResolve_TelegramChannelResolvesToTxtExtension() {
        String result = resolver.resolve("telegram/otp", "en");
        Assertions.assertEquals("telegram/otp_en.txt", result);
    }
}