package bg.sit_varna.sit.si.testkit.mother;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationAttempt;
import bg.sit_varna.sit.si.entity.NotificationRecord;

public final class NotificationAttemptMother {

    private NotificationAttemptMother() {
    }

    public static Builder anAttempt() {
        return new Builder();
    }

    public static final class Builder {
        private NotificationRecord notification;
        private NotificationStatus status = NotificationStatus.SENT;
        private String errorMessage;
        private String providerResponse;

        public Builder forNotification(NotificationRecord notification) {
            this.notification = notification;
            return this;
        }

        public Builder withStatus(NotificationStatus status) {
            this.status = status;
            return this;
        }

        public Builder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            this.status = NotificationStatus.FAILED;
            return this;
        }

        public Builder withProviderResponse(String providerResponse) {
            this.providerResponse = providerResponse;
            return this;
        }

        public NotificationAttempt build() {
            NotificationAttempt attempt = new NotificationAttempt();
            attempt.setNotification(notification);
            attempt.setStatus(status);
            attempt.setErrorMessage(errorMessage);
            attempt.setProviderResponse(providerResponse);
            return attempt;
        }
    }
}
