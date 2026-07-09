package bg.sit_varna.sit.si.service.channel.telegram;

import bg.sit_varna.sit.si.config.channel.TelegramConfig;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.TelegramSendException;
import bg.sit_varna.sit.si.service.core.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class TelegramApiSender {

    private static final Logger LOG = Logger.getLogger(TelegramApiSender.class);
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TelegramConfig telegramConfig;
    private final MessageService messageService;

    @Inject
    public TelegramApiSender(TelegramConfig telegramConfig, MessageService messageService) {
        this.telegramConfig = telegramConfig;
        this.messageService = messageService;
    }

    public boolean isConfigured() {
        return telegramConfig.isConfigured();
    }

    @Retry(maxRetries = 3, delay = 2000, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30000)
    public Integer sendMessage(String chatId, String message, Map<String, Object> options, Locale locale) {
        LOG.infof("Sending Telegram message to chat ID: %s", chatId);
        validateChatId(chatId);
        validateMessageLength(message);

        try {
            Map<String, Object> requestBody = buildSendMessageRequest(chatId, message, options);
            JsonNode response = executeApiCall("sendMessage", requestBody);

            Integer messageId = response.path("result").path("message_id").asInt();
            LOG.infof("Telegram message sent successfully. Chat ID: %s, Message ID: %d", chatId, messageId);
            return messageId;

        } catch (TelegramSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error sending Telegram message to %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    "Failed after retries: " + e.getMessage(),
                    chatId,
                    e
            );
        }
    }

    @Retry(maxRetries = 3, delay = 2000, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30000)
    public Integer sendPhoto(String chatId, String photo, String caption, Map<String, Object> options, Locale locale) {
        LOG.infof("Sending photo to chat ID: %s", chatId);

        validateChatId(chatId);
        if (caption != null && caption.length() > 1024) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_INVALID_PARAMETERS,
                    "Invalid Parameters",
                    "Photo caption must not exceed 1024 characters",
                    chatId
            );
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("photo", photo);

            if (caption != null && !caption.isEmpty()) {
                requestBody.put("caption", caption);
            }

            applyCommonOptions(requestBody, options);

            JsonNode response = executeApiCall("sendPhoto", requestBody);
            Integer messageId = response.path("result").path("message_id").asInt();

            LOG.infof("Photo sent successfully. Chat ID: %s, Message ID: %d", chatId, messageId);
            return messageId;

        } catch (TelegramSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error sending photo to chat ID: %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale, e.getMessage()),
                    chatId,
                    e
            );
        }
    }

    @Retry(maxRetries = 3, delay = 2000, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30000)
    public Integer sendDocument(String chatId, String document, String caption, Map<String, Object> options, Locale locale) {
        LOG.infof("Sending document to chat ID: %s", chatId);

        validateChatId(chatId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("document", document);

            if (caption != null && !caption.isEmpty()) {
                requestBody.put("caption", caption);
            }

            applyCommonOptions(requestBody, options);

            JsonNode response = executeApiCall("sendDocument", requestBody);
            Integer messageId = response.path("result").path("message_id").asInt();

            LOG.infof("Document sent successfully. Chat ID: %s, Message ID: %d", chatId, messageId);
            return messageId;

        } catch (TelegramSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error sending document to chat ID: %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale, e.getMessage()),
                    chatId,
                    e
            );
        }
    }

    @Retry(maxRetries = 3, delay = 2000, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30000)
    public boolean editMessageText(String chatId, Integer messageId, String newText, Map<String, Object> options, Locale locale) {
        LOG.infof("Editing message %d in chat %s", messageId, chatId);

        validateChatId(chatId);
        validateMessageLength(newText);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("message_id", messageId);
            requestBody.put("text", newText);

            applyCommonOptions(requestBody, options);

            executeApiCall("editMessageText", requestBody);
            LOG.infof("Message edited successfully. Chat ID: %s, Message ID: %d", chatId, messageId);
            return true;

        } catch (TelegramSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error editing message in chat ID: %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale, e.getMessage()),
                    chatId,
                    e
            );
        }
    }

    @Retry(maxRetries = 3, delay = 2000, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30000)
    public boolean deleteMessage(String chatId, Integer messageId, Locale locale) {
        LOG.infof("Deleting message %d from chat %s", messageId, chatId);

        validateChatId(chatId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("message_id", messageId);

            executeApiCall("deleteMessage", requestBody);
            LOG.infof("Message deleted successfully. Chat ID: %s, Message ID: %d", chatId, messageId);
            return true;

        } catch (TelegramSendException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting message in chat ID: %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale, e.getMessage()),
                    chatId,
                    e
            );
        }
    }

    public boolean sendChatAction(String chatId, String action) {
        LOG.debugf("Sending chat action '%s' to chat ID: %s", action, chatId);

        validateChatId(chatId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", chatId);
            requestBody.put("action", action);

            executeApiCall("sendChatAction", requestBody);
            return true;

        } catch (Exception e) {
            LOG.warnf("Failed to send chat action to %s: %s", chatId, e.getMessage());
            return false;
        }
    }

    public JsonNode getMe() {
        try {
            return executeApiCall("getMe", null);
        } catch (Exception e) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    "Bot Info Error",
                    "Failed to retrieve bot information: " + e.getMessage(),
                    null,
                    e
            );
        }
    }

    private Map<String, Object> buildSendMessageRequest(String chatId, String message, Map<String, Object> options) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("chat_id", chatId);
        requestBody.put("text", message);

        String parseMode = telegramConfig.defaultParseMode();
        if (options != null && options.containsKey("parse_mode")) {
            parseMode = options.get("parse_mode").toString();
        }
        if (parseMode != null && !parseMode.isEmpty() && !"none".equalsIgnoreCase(parseMode)) {
            requestBody.put("parse_mode", parseMode);
        }

        applyCommonOptions(requestBody, options);

        return requestBody;
    }

    private void applyCommonOptions(Map<String, Object> requestBody, Map<String, Object> options) {
        if (options == null) {
            return;
        }

        if (options.containsKey("disable_web_page_preview")) {
            requestBody.put("disable_web_page_preview", options.get("disable_web_page_preview"));
        } else {
            requestBody.put("disable_web_page_preview", telegramConfig.disableWebPagePreview());
        }

        if (options.containsKey("disable_notification") || options.containsKey("silent")) {
            boolean silent = options.containsKey("silent")
                    ? Boolean.parseBoolean(options.get("silent").toString())
                    : Boolean.parseBoolean(options.get("disable_notification").toString());
            requestBody.put("disable_notification", silent);
        }

        if (options.containsKey("reply_to_message_id")) {
            requestBody.put("reply_to_message_id", options.get("reply_to_message_id"));
            requestBody.put("allow_sending_without_reply",
                    telegramConfig.allowSendingWithoutReply());
        }

        if (options.containsKey("reply_markup")) {
            requestBody.put("reply_markup", options.get("reply_markup"));
        }
    }

    private JsonNode executeApiCall(String method, Map<String, Object> requestBody) throws Exception {
        String url = TELEGRAM_API_BASE + telegramConfig.botToken() + "/" + method;

        try (Client client = createHttpClient()) {
            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(requestBody != null ? requestBody : new HashMap<>()));

            return handleApiResponse(response, method);
        }
    }

    private JsonNode handleApiResponse(Response response, String method) throws Exception {
        int statusCode = response.getStatus();
        String responseBody = response.readEntity(String.class);

        JsonNode jsonResponse = OBJECT_MAPPER.readTree(responseBody);

        if (statusCode == 200 && jsonResponse.path("ok").asBoolean()) {
            LOG.debugf("Telegram API call successful: %s", method);
            return jsonResponse;
        }

        String errorDescription = jsonResponse.path("description").asText("Unknown error");
        int errorCode = jsonResponse.path("error_code").asInt(statusCode);

        LOG.errorf("Telegram API error - Method: %s, Status: %d, Code: %d, Description: %s",
                method, statusCode, errorCode, errorDescription);

        throw new TelegramSendException(
                determineErrorCode(errorCode),
                "Telegram API Error",
                String.format("API call failed: %s (error code: %d)", errorDescription, errorCode),
                null
        );
    }

    private Client createHttpClient() {
        return ClientBuilder.newBuilder().build();
    }

    private NotificationErrorCode determineErrorCode(int telegramErrorCode) {
        return switch (telegramErrorCode) {
            case 400 -> NotificationErrorCode.TELEGRAM_INVALID_PARAMETERS;
            case 401 -> NotificationErrorCode.TELEGRAM_BOT_ERROR;
            case 403 -> NotificationErrorCode.TELEGRAM_INVALID_RECIPIENT;
            case 429 -> NotificationErrorCode.TELEGRAM_RATE_LIMITED;
            default -> NotificationErrorCode.TELEGRAM_SEND_FAILED;
        };
    }

    private void validateChatId(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_INVALID_RECIPIENT,
                    "Invalid Chat ID",
                    "Chat ID cannot be null or empty",
                    chatId
            );
        }
    }

    private void validateMessageLength(String message) {
        if (message == null) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_INVALID_PARAMETERS,
                    "Invalid Message",
                    "Message text cannot be null",
                    null
            );
        }
        if (message.length() > 4096) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_INVALID_PARAMETERS,
                    "Message Too Long",
                    String.format("Message exceeds 4096 characters (%d)", message.length()),
                    null
            );
        }
    }
}
