package bg.sit_varna.sit.si.api;

import bg.sit_varna.sit.si.testkit.base.ApiTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

/**
 * Verifies signature checking with a real, test-generated EC key pair (matching the
 * pattern in WebhookServiceTest) rather than trying to satisfy this machine's real
 * SendGrid webhook public key from .env - see TelegramApiSenderIntegrationTest for the
 * same .env-precedence issue. The public key is forced via a QuarkusTestProfile
 * override; the matching private key signs the test payload.
 */
@QuarkusTest
@TestProfile(WebhookResourceApiTest.FakeWebhookKeyProfile.class)
class WebhookResourceApiTest extends ApiTestBase {

    private static final String TIMESTAMP = "1690000000";
    private static final String PAYLOAD = "[{"
            + "\"email\":\"user@example.com\","
            + "\"timestamp\":1690000000,"
            + "\"event\":\"delivered\","
            + "\"sg_message_id\":\"sg-msg-id-123\","
            + "\"notificationId\":\"550e8400-e29b-41d4-a716-446655440000\""
            + "}]";

    @BeforeAll
    static void registerBouncyCastleProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void handleSendGridWebhook_validSignature_returns200() throws Exception {
        String signature = sign(FakeWebhookKeyProfile.KEY_PAIR.getPrivate(), TIMESTAMP, PAYLOAD);

        apiRequest()
                .header("X-Twilio-Email-Event-Webhook-Signature", signature)
                .header("X-Twilio-Email-Event-Webhook-Timestamp", TIMESTAMP)
                .body(PAYLOAD)
                .when()
                .post("/api/v1/webhooks/sendgrid")
                .then()
                .statusCode(200);
    }

    @Test
    void handleSendGridWebhook_invalidSignature_returns401WithEmptyBody() {
        // WebhookResource.handleSendGridWebhook returns a bare 401 (no entity) on
        // SecurityException, so there's no ErrorResponse body to assert here.
        apiRequest()
                .header("X-Twilio-Email-Event-Webhook-Signature", "garbage-signature")
                .header("X-Twilio-Email-Event-Webhook-Timestamp", TIMESTAMP)
                .body(PAYLOAD)
                .when()
                .post("/api/v1/webhooks/sendgrid")
                .then()
                .statusCode(401)
                .body(org.hamcrest.Matchers.emptyOrNullString());
    }

    private static String sign(PrivateKey privateKey, String timestamp, String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA", "BC");
        signer.initSign(privateKey);
        signer.update(timestamp.getBytes(StandardCharsets.UTF_8));
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    public static class FakeWebhookKeyProfile implements QuarkusTestProfile {
        static final KeyPair KEY_PAIR = generateKeyPair();

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sendgrid.webhook-public-key",
                    Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
        }

        private static KeyPair generateKeyPair() {
            try {
                return KeyPairGenerator.getInstance("EC").generateKeyPair();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
