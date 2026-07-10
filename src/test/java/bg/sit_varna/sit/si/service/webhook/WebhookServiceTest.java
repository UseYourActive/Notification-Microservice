package bg.sit_varna.sit.si.service.webhook;

import bg.sit_varna.sit.si.config.channel.SendGridConfig;
import bg.sit_varna.sit.si.dto.event.SendGridEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookService}, covering:
 * <ul>
 *     <li>the signature-verification branches (dev-mode bypass, null signature/timestamp,
 *     valid/invalid real ECDSA signatures, malformed key/signature crypto failures);</li>
 *     <li>the JSON parse/process happy path and the swallowed-exception path (SendGrid
 *     must always receive 200 OK, even on malformed payloads).</li>
 * </ul>
 *
 * <p>The SendGrid {@code EventWebhook} helper hard-codes the "BC" (BouncyCastle) security
 * provider for both {@code ConvertPublicKeyToECDSA} and {@code VerifySignature}. That provider
 * is not registered automatically just by having {@code bcprov-jdk18on} on the classpath, so it
 * is registered once here before any crypto-exercising test runs.</p>
 */
class WebhookServiceTest {

    private SendGridConfig sendGridConfig;
    private WebhookProcessor webhookProcessor;
    private WebhookService webhookService;

    @BeforeAll
    static void registerBouncyCastleProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() {
        sendGridConfig = mock(SendGridConfig.class);
        webhookProcessor = mock(WebhookProcessor.class);
        webhookService = new WebhookService(sendGridConfig, new ObjectMapper(), webhookProcessor);
    }

    @Test
    void verifyAndProcess_publicKeyNotConfigured_devModeBypassesVerificationAndProcessesEvents() {
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.empty());
        String payload = validEventArrayJson("notif-1", "delivered");

        webhookService.verifyAndProcess("garbage-signature", "garbage-timestamp", payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SendGridEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(webhookProcessor).processEvents(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("notif-1", captor.getValue().get(0).getNotificationId());
        assertEquals("delivered", captor.getValue().get(0).getEvent());
    }

    @Test
    void verifyAndProcess_validRealSignature_processesEvents() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(keyPair.getPublic())));

        String timestamp = "1690000000";
        String payload = validEventArrayJson("notif-2", "bounce");
        String signature = sign(keyPair.getPrivate(), timestamp, payload);

        webhookService.verifyAndProcess(signature, timestamp, payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SendGridEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(webhookProcessor).processEvents(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("notif-2", captor.getValue().get(0).getNotificationId());
        assertEquals("bounce", captor.getValue().get(0).getEvent());
    }

    @Test
    void verifyAndProcess_signatureFromUnrelatedKeyPair_throwsSecurityExceptionAndNeverProcesses() throws Exception {
        KeyPair configuredKeyPair = generateEcKeyPair();
        KeyPair attackerKeyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(configuredKeyPair.getPublic())));

        String timestamp = "1690000000";
        String payload = validEventArrayJson("notif-3", "delivered");
        // Signed with a key that does NOT match the configured public key.
        String signature = sign(attackerKeyPair.getPrivate(), timestamp, payload);

        assertThrows(SecurityException.class,
                () -> webhookService.verifyAndProcess(signature, timestamp, payload));

        verifyNoInteractions(webhookProcessor);
    }

    @Test
    void verifyAndProcess_nullSignatureWithKeyConfigured_throwsSecurityExceptionAndNeverProcesses() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(keyPair.getPublic())));

        assertThrows(SecurityException.class,
                () -> webhookService.verifyAndProcess(null, "1690000000", validEventArrayJson("notif-4", "delivered")));

        verify(webhookProcessor, never()).processEvents(any());
    }

    @Test
    void verifyAndProcess_nullTimestampWithKeyConfigured_throwsSecurityExceptionAndNeverProcesses() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(keyPair.getPublic())));

        assertThrows(SecurityException.class,
                () -> webhookService.verifyAndProcess("some-signature", null, validEventArrayJson("notif-5", "delivered")));

        verify(webhookProcessor, never()).processEvents(any());
    }

    @Test
    void verifyAndProcess_malformedPublicKeyConfigured_failsClosedWithSecurityExceptionAndNeverProcesses() {
        // Not valid Base64 / not a parsable X.509 EC key -> ConvertPublicKeyToECDSA throws,
        // caught by the isSignatureValid catch block, which must fail closed (return false).
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of("%%%not-a-valid-base64-key%%%"));

        assertThrows(SecurityException.class,
                () -> webhookService.verifyAndProcess("some-signature", "1690000000",
                        validEventArrayJson("notif-6", "delivered")));

        verifyNoInteractions(webhookProcessor);
    }

    @Test
    void verifyAndProcess_malformedSignatureEncoding_failsClosedWithSecurityExceptionAndNeverProcesses() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(keyPair.getPublic())));

        // Not valid Base64 -> VerifySignature's decode throws, caught by the isSignatureValid
        // catch block, which must fail closed (return false).
        assertThrows(SecurityException.class,
                () -> webhookService.verifyAndProcess("%%%not-valid-base64-signature%%%", "1690000000",
                        validEventArrayJson("notif-7", "delivered")));

        verifyNoInteractions(webhookProcessor);
    }

    @Test
    void verifyAndProcess_malformedJsonPayloadWithValidSignature_swallowsExceptionAndNeverProcesses() throws Exception {
        KeyPair keyPair = generateEcKeyPair();
        when(sendGridConfig.webhookPublicKey()).thenReturn(Optional.of(encodePublicKey(keyPair.getPublic())));

        String timestamp = "1690000000";
        String malformedPayload = "{ this is not valid JSON ]";
        String signature = sign(keyPair.getPrivate(), timestamp, malformedPayload);

        // Must not throw - SendGrid must always get 200 OK, even for a payload we can't parse.
        webhookService.verifyAndProcess(signature, timestamp, malformedPayload);

        verifyNoInteractions(webhookProcessor);
    }

    private static String validEventArrayJson(String notificationId, String event) {
        return "[{"
                + "\"email\":\"user@example.com\","
                + "\"timestamp\":1690000000,"
                + "\"event\":\"" + event + "\","
                + "\"sg_message_id\":\"sg-msg-id-123\","
                + "\"notificationId\":\"" + notificationId + "\""
                + "}]";
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        return generator.generateKeyPair();
    }

    private static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    private static String sign(PrivateKey privateKey, String timestamp, String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA", "BC");
        signer.initSign(privateKey);
        signer.update(timestamp.getBytes(StandardCharsets.UTF_8));
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }
}
