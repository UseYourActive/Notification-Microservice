package bg.sit_varna.sit.si.service.channel.telegram;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.TelegramSendException;
import bg.sit_varna.sit.si.testkit.wiremock.WireMockTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses an obviously-fake bot token ("000000:TEST-TOKEN") because the token is a URL
 * path segment and therefore appears verbatim in WireMock stub definitions and any
 * failure logs. Forced via a QuarkusTestProfile config override rather than the %test
 * profile in application.properties: a local .env file (dotenv config source, higher
 * priority than application.properties) can otherwise leak a real-looking
 * TELEGRAM_BOT_TOKEN into the test - which is exactly the scenario this fake token
 * exists to avoid.
 */
@QuarkusTest
@TestProfile(TelegramApiSenderIntegrationTest.FakeBotTokenProfile.class)
class TelegramApiSenderIntegrationTest extends WireMockTestBase {

    private static final String BOT_TOKEN = "000000:TEST-TOKEN";
    private static final String SEND_MESSAGE_PATH = "/bot" + BOT_TOKEN + "/sendMessage";

    public static class FakeBotTokenProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("telegram.bot.token", BOT_TOKEN);
        }
    }

    @Inject
    TelegramApiSender telegramApiSender;

    @Test
    void sendMessage_success_sendsExpectedRequestAndReturnsMessageId() {
        wireMock().stubFor(post(urlEqualTo(SEND_MESSAGE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":{\"message_id\":42}}")));

        Integer messageId = telegramApiSender.sendMessage("12345", "hello", Map.of(), Locale.ENGLISH);

        assertThat(messageId).isEqualTo(42);
        wireMock().verify(postRequestedFor(urlEqualTo(SEND_MESSAGE_PATH))
                .withRequestBody(matchingJsonPath("$.chat_id", equalTo("12345")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("hello"))));
    }

    @ParameterizedTest
    @CsvSource({
            "400,TELEGRAM_INVALID_PARAMETERS",
            "401,TELEGRAM_BOT_ERROR",
            "403,TELEGRAM_INVALID_RECIPIENT",
            "429,TELEGRAM_RATE_LIMITED",
            "500,TELEGRAM_SEND_FAILED"
    })
    void sendMessage_providerError_throwsMappedException(int providerStatus, String expectedErrorCode) {
        wireMock().stubFor(post(urlEqualTo(SEND_MESSAGE_PATH))
                .willReturn(aResponse()
                        .withStatus(providerStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":false,\"error_code\":" + providerStatus + ",\"description\":\"simulated failure\"}")));

        assertThatThrownBy(() -> telegramApiSender.sendMessage("12345", "hello", Map.of(), Locale.ENGLISH))
                .isInstanceOf(TelegramSendException.class)
                .extracting(e -> ((TelegramSendException) e).getErrorCode())
                .isEqualTo(NotificationErrorCode.valueOf(expectedErrorCode));
    }
}
