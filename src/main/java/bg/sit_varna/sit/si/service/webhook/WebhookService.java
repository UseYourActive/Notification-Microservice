package bg.sit_varna.sit.si.service.webhook;

import bg.sit_varna.sit.si.config.channel.SendGridConfig;
import bg.sit_varna.sit.si.dto.event.SendGridEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.helpers.eventwebhook.EventWebhook;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.security.interfaces.ECPublicKey;
import java.util.List;

@ApplicationScoped
public class WebhookService {

    private static final Logger LOG = Logger.getLogger(WebhookService.class);

    private final SendGridConfig sendGridConfig;
    private final ObjectMapper objectMapper;
    private final WebhookProcessor webhookProcessor;

    @Inject
    public WebhookService(SendGridConfig sendGridConfig, ObjectMapper objectMapper, WebhookProcessor webhookProcessor) {
        this.sendGridConfig = sendGridConfig;
        this.objectMapper = objectMapper;
        this.webhookProcessor = webhookProcessor;
    }

    public void verifyAndProcess(String signature, String timestamp, String rawPayload) {
        if (!isSignatureValid(signature, timestamp, rawPayload)) {
            LOG.warn("⚠️ Invalid SendGrid Signature! Ignoring request.");
            throw new SecurityException("Invalid Webhook Signature");
        }

        try {
            List<SendGridEvent> events = objectMapper.readValue(
                    rawPayload,
                    new TypeReference<>() {
                    }
            );

            webhookProcessor.processEvents(events);

        } catch (Exception e) {
            LOG.error("Failed to parse or process SendGrid payload", e);
            // We do not throw an exception here to ensure we return 200 OK to SendGrid
        }
    }

    private boolean isSignatureValid(String signature, String timestamp, String payload) {
        if (sendGridConfig.webhookPublicKey().isEmpty()) {
            LOG.warn("SendGrid Public Key not configured - Skipping verification (DEV MODE)");
            return true;
        }

        if (signature == null || timestamp == null) return false;

        try {
            EventWebhook eventWebhook = new EventWebhook();
            ECPublicKey publicKey = eventWebhook.ConvertPublicKeyToECDSA(sendGridConfig.webhookPublicKey().get());
            return eventWebhook.VerifySignature(publicKey, payload, signature, timestamp);
        } catch (Exception e) {
            LOG.error("Crypto error verifying signature", e);
            return false;
        }
    }
}
