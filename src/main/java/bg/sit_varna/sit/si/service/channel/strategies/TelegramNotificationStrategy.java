package bg.sit_varna.sit.si.service.channel.strategies;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.exception.exceptions.TelegramSendException;
import bg.sit_varna.sit.si.service.channel.telegram.TelegramApiSender;
import bg.sit_varna.sit.si.service.core.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public final class TelegramNotificationStrategy implements ChannelStrategy {

    private static final Logger LOG = Logger.getLogger(TelegramNotificationStrategy.class);

    private final TelegramApiSender telegramApiSender;
    private final MessageService messageService;

    @Inject
    public TelegramNotificationStrategy(TelegramApiSender telegramApiSender, MessageService messageService) {
        this.telegramApiSender = telegramApiSender;
        this.messageService = messageService;
    }

    @Override
    public void send(Notification notification) {
        Locale locale = Locale.forLanguageTag(notification.getLocale());
        String chatId = notification.getRecipient();
        LOG.infof("Sending Telegram notification to chat ID: %s", chatId);

        if (!telegramApiSender.isConfigured()) {
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale,
                            "Telegram is not configured. Please set telegram.bot.token"),
                    chatId
            );
        }

        // Extract options from notification data
        Map<String, Object> data = notification.getData();
        Map<String, Object> options = data != null ? new HashMap<>(data) : new HashMap<>();

        // Show typing indicator if requested
        boolean showTyping = Boolean.parseBoolean(String.valueOf(options.getOrDefault("typing_indicator", "false")));
        if (showTyping) {
            try {
                telegramApiSender.sendChatAction(chatId, "typing");
                // Brief pause to let user see typing indicator
                Thread.sleep(1000);
            } catch (Exception e) {
                LOG.warnf("Failed to send typing indicator: %s", e.getMessage());
            }
        }

        try {
            String messageType = String.valueOf(options.getOrDefault("message_type", "text"));
            Integer messageId;

            switch (messageType.toLowerCase()) {
                case "photo" -> {
                    String photoUrl = String.valueOf(options.get("media_url"));
                    String caption = String.valueOf(options.getOrDefault("caption", notification.getProcessedContent()));
                    messageId = telegramApiSender.sendPhoto(chatId, photoUrl, caption, options, locale);
                }
                case "document" -> {
                    String docUrl = String.valueOf(options.get("media_url"));
                    String caption = String.valueOf(options.getOrDefault("caption", notification.getProcessedContent()));
                    messageId = telegramApiSender.sendDocument(chatId, docUrl, caption, options, locale);
                }
                default -> {
                    messageId = telegramApiSender.sendMessage(
                            chatId,
                            notification.getProcessedContent(),
                            options,
                            locale
                    );
                }
            }

            LOG.infof("Telegram notification sent successfully. Chat ID: %s, Message ID: %d", chatId, messageId);

        } catch (TelegramSendException e) {
            LOG.errorf("Failed to send Telegram notification to %s: %s", chatId, e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error sending Telegram notification to %s", chatId);
            throw new TelegramSendException(
                    NotificationErrorCode.TELEGRAM_BOT_ERROR,
                    messageService.getTitle(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale),
                    messageService.getMessage(NotificationErrorCode.TELEGRAM_BOT_ERROR, locale, e.getMessage()),
                    chatId,
                    e
            );
        }
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM;
    }
}
