package bg.sit_varna.sit.si.service.channel.email;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.EmailSendException;
import bg.sit_varna.sit.si.testkit.wiremock.WireMockTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses obviously-fake SendGrid credentials forced via a QuarkusTestProfile config
 * override rather than the %test profile in application.properties: this machine's
 * local .env file (dotenv config source, higher priority than application.properties)
 * otherwise leaks a real-looking SENDGRID_API_KEY and the developer's real
 * SENDGRID_FROM_EMAIL into the test - see TelegramApiSenderIntegrationTest for the
 * same issue with the bot token.
 */
@QuarkusTest
@TestProfile(SendGridEmailSenderIntegrationTest.FakeSendGridCredentialsProfile.class)
class SendGridEmailSenderIntegrationTest extends WireMockTestBase {

    private static final String MAIL_SEND_PATH = "/v3/mail/send";

    @Inject
    EmailSender emailSender;

    @Test
    void send_success_sendsExpectedMailPayload() {
        wireMock().stubFor(post(urlEqualTo(MAIL_SEND_PATH))
                .willReturn(aResponse().withStatus(202)));

        emailSender.send(
                "recipient@example.com",
                "Welcome",
                "<p>hello</p>",
                List.of(),
                List.of(),
                Locale.ENGLISH,
                Map.of()
        );

        wireMock().verify(postRequestedFor(urlEqualTo(MAIL_SEND_PATH))
                .withRequestBody(matchingJsonPath("$.personalizations[0].to[0].email", equalTo("recipient@example.com")))
                .withRequestBody(matchingJsonPath("$.subject", equalTo("Welcome")))
                .withRequestBody(matchingJsonPath("$.content[0].value", equalTo("<p>hello</p>")))
                .withRequestBody(matchingJsonPath("$.from.email", equalTo("no-reply@test.local"))));
    }

    @ParameterizedTest
    @CsvSource({
            "400,EMAIL_INVALID_RECIPIENT",
            "401,EMAIL_CONFIGURATION_ERROR",
            "403,EMAIL_CONFIGURATION_ERROR",
            "429,EMAIL_SEND_FAILED",
            "500,EMAIL_SEND_FAILED"
    })
    void send_providerError_throwsMappedException(int providerStatus, String expectedErrorCode) {
        wireMock().stubFor(post(urlEqualTo(MAIL_SEND_PATH))
                .willReturn(aResponse()
                        .withStatus(providerStatus)
                        .withBody("{\"errors\":[{\"message\":\"simulated failure\"}]}")));

        assertThatThrownBy(() -> emailSender.send(
                "recipient@example.com",
                "Welcome",
                "<p>hello</p>",
                List.of(),
                List.of(),
                Locale.ENGLISH,
                Map.of()
        ))
                .isInstanceOf(EmailSendException.class)
                .extracting(e -> ((EmailSendException) e).getErrorCode())
                .isEqualTo(NotificationErrorCode.valueOf(expectedErrorCode));
    }

    public static class FakeSendGridCredentialsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "sendgrid.api-key", "SG.test-fake-key",
                    "sendgrid.from-email", "no-reply@test.local"
            );
        }
    }
}
