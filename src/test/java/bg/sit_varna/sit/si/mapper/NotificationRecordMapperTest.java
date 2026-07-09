package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit test - NotificationRecordMapper is a pure field-by-field mapping with
 * no CDI dependencies, so no @QuarkusTest boot is needed.
 */
class NotificationRecordMapperTest {

    private final NotificationRecordMapper mapper = new NotificationRecordMapper();

    @Test
    void toNotificationCopiesEveryFieldTheProcessorNeedsToReprocessTheRow() {
        NotificationRecord record = new NotificationRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRecipient("mapper-test@example.com");
        record.setChannel(NotificationChannel.EMAIL);
        record.setTemplateName("welcome");
        record.setLocale("en");
        record.setMessage("Hello there");
        record.setStatus(NotificationStatus.PROCESSING);
        record.setPayload(Map.of("name", "Alex"));

        Notification notification = mapper.toNotification(record);

        assertEquals(record.getId(), notification.getId());
        assertEquals(record.getRecipient(), notification.getRecipient());
        assertEquals(record.getChannel(), notification.getChannel());
        assertEquals(record.getTemplateName(), notification.getTemplateName());
        assertEquals(record.getLocale(), notification.getLocale());
        assertEquals(record.getMessage(), notification.getMessage());
        assertEquals(record.getPayload(), notification.getData());
    }
}
