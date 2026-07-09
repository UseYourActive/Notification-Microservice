package bg.sit_varna.sit.si.service.channel.sms;

import bg.sit_varna.sit.si.config.channel.TwilioConfig;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.SmsSendException;
import bg.sit_varna.sit.si.service.core.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@ApplicationScoped
public final class TwilioSmsSender implements SmsSender {

    private static final Logger LOG = Logger.getLogger(TwilioSmsSender.class);
    private static final String TWILIO_API_URL_TEMPLATE = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final TwilioConfig twilioConfig;
    private final MessageService messageService;

    @Inject
    public TwilioSmsSender(TwilioConfig twilioConfig, MessageService messageService) {
        this.twilioConfig = twilioConfig;
        this.messageService = messageService;
    }

    public boolean isConfigured() {
        return twilioConfig.isConfigured();
    }

    @Override
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 3, delay = 1000)
    public void send(String recipient, String message, Locale locale) {
        LOG.infof("Sending SMS via Twilio to: %s", recipient);

        try (Client client = ClientBuilder.newClient()) {
            String url = String.format(TWILIO_API_URL_TEMPLATE, twilioConfig.accountSid());
            String authorization = buildAuthorizationHeader();

            Form form = new Form();
            form.param("From", twilioConfig.phoneNumber());
            form.param("To", recipient);
            form.param("Body", message);

            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization)
                    .post(Entity.form(form));

            handleResponse(response, recipient, locale);

        } catch (SmsSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error sending SMS via Twilio to: %s", recipient);
            throw new SmsSendException(
                    NotificationErrorCode.SMS_PROVIDER_ERROR,
                    messageService.getTitle(NotificationErrorCode.SMS_PROVIDER_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.SMS_PROVIDER_ERROR,
                            locale,
                            "Twilio",
                            e.getMessage()),
                    recipient,
                    "Twilio",
                    e
            );
        }
    }

    @Override
    public String getProviderName() {
        return SmsProvider.TWILIO.getProvider();
    }

    private String buildAuthorizationHeader() {
        String credentials = twilioConfig.accountSid() + ":" + twilioConfig.authToken();
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    private void handleResponse(Response response, String recipient, Locale locale) {
        int statusCode = response.getStatus();

        if (statusCode == 201) {
            LOG.infof("SMS sent successfully via Twilio to: %s", recipient);
            LOG.debugf("Twilio response: %s", response.readEntity(String.class));
        } else {
            String responseBody = response.readEntity(String.class);
            LOG.errorf("Twilio API error - Status: %d, Body: %s", statusCode, responseBody);

            throw new SmsSendException(
                    NotificationErrorCode.SMS_SEND_FAILED,
                    messageService.getTitle(NotificationErrorCode.SMS_SEND_FAILED, locale),
                    messageService.getMessage(NotificationErrorCode.SMS_SEND_FAILED,
                            locale,
                            recipient,
                            "Twilio",
                            "API returned status " + statusCode),
                    recipient,
                    "Twilio"
            );
        }
    }
}
