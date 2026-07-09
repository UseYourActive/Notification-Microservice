package bg.sit_varna.sit.si.exception.exceptions;

import bg.sit_varna.sit.si.constant.ErrorCategory;
import bg.sit_varna.sit.si.constant.NotificationErrorCode;

public class DuplicateNotificationException extends NotificationException {

    public DuplicateNotificationException(String title, String detail) {
        super(NotificationErrorCode.DUPLICATE_NOTIFICATION, ErrorCategory.CONFLICT, title, detail);
    }
}
