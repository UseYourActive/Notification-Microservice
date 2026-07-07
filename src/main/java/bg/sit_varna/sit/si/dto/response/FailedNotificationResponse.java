package bg.sit_varna.sit.si.dto.response;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Summary of a failed notification")
public record FailedNotificationResponse(

        @Schema(description = "Unique notification identifier",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String notificationId,

        @Schema(description = "Recipient identifier",
                example = "ivan.petrov@example.com")
        String recipient,

        @Schema(description = "Notification channel",
                example = "EMAIL")
        String channel,

        @Schema(description = "Template used for the notification",
                example = "email/welcome")
        String templateName,

        @Schema(description = "Notification status",
                example = "FAILED")
        NotificationStatus status,

        @Schema(description = "Timestamp the notification was created",
                example = "2025-10-26T14:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Timestamp the notification was last updated",
                example = "2025-10-26T14:31:12")
        LocalDateTime updatedAt
) {
    public static FailedNotificationResponse from(NotificationRecord record) {
        return new FailedNotificationResponse(
                record.getId(),
                record.getRecipient(),
                record.getChannel() != null ? record.getChannel().toString() : null,
                record.getTemplateName(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
