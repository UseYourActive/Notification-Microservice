package bg.sit_varna.sit.si.testkit.mother;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NotificationRecordMother {

    private NotificationRecordMother() {
    }

    public static Builder aNotification() {
        return new Builder();
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String recipient = "mother-test@example.com";
        private NotificationChannel channel = NotificationChannel.EMAIL;
        private String templateName = "email/welcome";
        private Locale locale = Locale.ENGLISH;
        private String message;
        private NotificationStatus status = NotificationStatus.QUEUED;
        private Map<String, Object> payload = Map.of();
        private String lockedBy;
        private LocalDateTime lockedAt;
        private int attemptsCount = 0;

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withRecipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder withChannel(NotificationChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder withTemplateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder withLocale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withStatus(NotificationStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPayload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder withAttempts(int attemptsCount) {
            this.attemptsCount = attemptsCount;
            return this;
        }

        public Builder lockedBy(String workerId) {
            this.lockedBy = workerId;
            this.lockedAt = LocalDateTime.now();
            return this;
        }

        public Builder queued() {
            this.status = NotificationStatus.QUEUED;
            return this;
        }

        public Builder processing() {
            this.status = NotificationStatus.PROCESSING;
            return this;
        }

        public Builder sent() {
            this.status = NotificationStatus.SENT;
            return this;
        }

        public Builder failed() {
            this.status = NotificationStatus.FAILED;
            return this;
        }

        public NotificationRecord build() {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient(recipient);
            record.setChannel(channel);
            record.setTemplateName(templateName);
            record.setLocale(locale);
            record.setMessage(message);
            record.setStatus(status);
            record.setPayload(payload);
            record.setAttemptsCount(attemptsCount);
            record.setLockedBy(lockedBy);
            record.setLockedAt(lockedAt);
            return record;
        }
    }
}
