package bg.sit_varna.sit.si.service.channel.email;

import bg.sit_varna.sit.si.config.channel.SendGridConfig;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.EmailSendException;
import bg.sit_varna.sit.si.service.core.MessageService;
import com.sendgrid.Client;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class SendGridEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SendGridEmailSender.class);

    private final SendGridConfig sendGridConfig;
    private final MessageService messageService;

    @Inject
    public SendGridEmailSender(SendGridConfig sendGridConfig, MessageService messageService) {
        this.sendGridConfig = sendGridConfig;
        this.messageService = messageService;
    }

    public boolean isConfigured() {
        return sendGridConfig.isConfigured();
    }

    @Override
    public String getProviderName() {
        return EmailProvider.SENDGRID.getProvider();
    }

    @Override
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 3, delay = 1000)
    public void send(String to, String subject,
                     String content,
                     List<String> ccList,
                     List<String> bccList,
                     Locale locale,
                     Map<String, String> metadata) {
        LOG.debugf("Preparing SendGrid request for: %s", to);

        try {
            SendGrid sendGrid = buildClient();
            Mail mail = buildMail(to, subject, content, ccList, bccList, metadata);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);
            handleResponse(response, to, locale);

        } catch (EmailSendException e) {
            throw e;
        } catch (IOException e) {
            LOG.errorf(e, "IO error sending email via SendGrid to: %s", to);
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_SEND_FAILED,
                    messageService.getTitle(NotificationErrorCode.EMAIL_SEND_FAILED, locale),
                    messageService.getMessage(NotificationErrorCode.EMAIL_SEND_FAILED, locale, to, "Network error: " + e.getMessage()),
                    to,
                    e
            );
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error sending email via SendGrid to: %s", to);
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_SEND_FAILED,
                    messageService.getTitle(NotificationErrorCode.EMAIL_SEND_FAILED, locale),
                    messageService.getMessage(NotificationErrorCode.EMAIL_SEND_FAILED, locale, to, e.getMessage()),
                    to,
                    e
            );
        }
    }

    private SendGrid buildClient() {
        return sendGridConfig.apiBaseUrl()
                .map(baseUrl -> {
                    SendGrid client = new SendGrid(sendGridConfig.apiKey(), new Client(true));
                    client.setHost(baseUrl);
                    return client;
                })
                .orElseGet(() -> new SendGrid(sendGridConfig.apiKey()));
    }

    private Mail buildMail(String to, String subject, String content, List<String> ccList, List<String> bccList, Map<String, String> metadata) {
        Email from = new Email(sendGridConfig.fromEmail(), sendGridConfig.fromName());
        Email recipient = new Email(to);
        Content emailContent = new Content("text/html", content);
        Mail mail = new Mail(from, subject, recipient, emailContent);

        if (ccList != null && !ccList.isEmpty()) {
            for (String cc : ccList) {
                mail.personalization.getFirst().addCc(new Email(cc));
            }
        }

        if (bccList != null && !bccList.isEmpty()) {
            for (String bcc : bccList) {
                mail.personalization.getFirst().addBcc(new Email(bcc));
            }
        }

        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                mail.addCustomArg(entry.getKey(), entry.getValue());
            }
        }

        return mail;
    }

    private void handleResponse(Response response, String recipient, Locale locale) {
        int statusCode = response.getStatusCode();

        if (statusCode >= 200 && statusCode < 300) {
            LOG.debugf("SendGrid accepted request for %s (Status: %d)", recipient, statusCode);
            return;
        }

        String errorMessage = response.getBody();
        LOG.warnf("SendGrid API error for %s: %d - %s", recipient, statusCode, errorMessage);

        if (statusCode == 400) {
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_INVALID_RECIPIENT,
                    messageService.getTitle(NotificationErrorCode.EMAIL_INVALID_RECIPIENT, locale),
                    messageService.getMessage(NotificationErrorCode.EMAIL_INVALID_RECIPIENT, locale, recipient) + " - " + errorMessage,
                    recipient
            );
        } else if (statusCode == 401 || statusCode == 403) {
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_CONFIGURATION_ERROR,
                    messageService.getTitle(NotificationErrorCode.EMAIL_CONFIGURATION_ERROR, locale),
                    "SendGrid authentication failed. Check API key.",
                    recipient
            );
        } else if (statusCode == 429) {
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_SEND_FAILED,
                    messageService.getTitle(NotificationErrorCode.EMAIL_SEND_FAILED, locale),
                    "Rate limit exceeded: " + errorMessage,
                    recipient
            );
        } else {
            throw new EmailSendException(
                    NotificationErrorCode.EMAIL_SEND_FAILED,
                    messageService.getTitle(NotificationErrorCode.EMAIL_SEND_FAILED, locale),
                    "HTTP " + statusCode + ": " + errorMessage,
                    recipient
            );
        }
    }
}
