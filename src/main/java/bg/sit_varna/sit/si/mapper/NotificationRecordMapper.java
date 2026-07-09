package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reconstructs the domain {@link Notification} the poller dispatches from a claimed
 * {@link NotificationRecord} row - the row is the queue's persisted source of truth,
 * so every field NotificationProcessor needs to reprocess a notification must be
 * mapped here.
 */
@ApplicationScoped
public class NotificationRecordMapper {

    public Notification toNotification(NotificationRecord record) {
        return Notification.builder()
                .id(record.getId())
                .recipient(record.getRecipient())
                .channel(record.getChannel())
                .templateName(record.getTemplateName())
                .locale(record.getLocale() != null ? record.getLocale().toLanguageTag() : null)
                .data(record.getPayload())
                .message(record.getMessage())
                .build();
    }
}
