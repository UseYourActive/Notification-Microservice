package bg.sit_varna.sit.si.service.channel.sms;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.SmsSendException;
import bg.sit_varna.sit.si.testkit.wiremock.WireMockTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses obviously-fake Twilio credentials forced via a QuarkusTestProfile config
 * override rather than the %test profile in application.properties: this machine's
 * local .env file (dotenv config source, higher priority than application.properties)
 * otherwise leaks real-looking TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN into the test -
 * see TelegramApiSenderIntegrationTest for the same issue with the bot token.
 */
@QuarkusTest
@TestProfile(TwilioSmsSenderIntegrationTest.FakeTwilioCredentialsProfile.class)
class TwilioSmsSenderIntegrationTest extends WireMockTestBase {

    private static final String ACCOUNT_SID = "ACtest00000000000000000000000000";
    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String MESSAGES_PATH = "/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json";

    @Inject
    SmsSender smsSender;

    @Test
    void send_success_sendsExpectedFormFieldsAndAuthHeader() {
        wireMock().stubFor(post(urlEqualTo(MESSAGES_PATH))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\":\"SMtest\",\"status\":\"queued\"}")));

        smsSender.send("+15551234567", "hello from test", Locale.ENGLISH);

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes());

        wireMock().verify(postRequestedFor(urlEqualTo(MESSAGES_PATH))
                .withHeader("Authorization", equalTo(expectedAuth))
                .withRequestBody(containing("To=%2B15551234567"))
                .withRequestBody(containing("Body=hello"))
                .withRequestBody(containing("From=")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500})
    void send_providerError_throwsSmsSendExceptionWithSendFailedCode(int providerStatus) {
        wireMock().stubFor(post(urlEqualTo(MESSAGES_PATH))
                .willReturn(aResponse()
                        .withStatus(providerStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":" + providerStatus + ",\"message\":\"simulated failure\"}")));

        assertThatThrownBy(() -> smsSender.send("+15551234567", "hello from test", Locale.ENGLISH))
                .isInstanceOf(SmsSendException.class)
                .extracting(e -> ((SmsSendException) e).getErrorCode())
                .isEqualTo(NotificationErrorCode.SMS_SEND_FAILED);
    }

    public static class FakeTwilioCredentialsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "twilio.account.sid", ACCOUNT_SID,
                    "twilio.auth.token", AUTH_TOKEN,
                    "twilio.phone.number", "+15550000000"
            );
        }
    }
}
