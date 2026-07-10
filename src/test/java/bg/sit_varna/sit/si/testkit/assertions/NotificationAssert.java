package bg.sit_varna.sit.si.testkit.assertions;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import org.assertj.core.api.AbstractAssert;

import java.util.Objects;
import java.util.Set;

public class NotificationAssert extends AbstractAssert<NotificationAssert, NotificationRecord> {

    private static final Set<NotificationStatus> TERMINAL_STATUSES =
            Set.of(NotificationStatus.SENT, NotificationStatus.FAILED);

    private NotificationAssert(NotificationRecord actual) {
        super(actual, NotificationAssert.class);
    }

    public static NotificationAssert assertThatNotification(NotificationRecord actual) {
        return new NotificationAssert(actual);
    }

    public NotificationAssert hasStatus(NotificationStatus expected) {
        isNotNull();
        if (actual.getStatus() != expected) {
            failWithMessage("Expected notification status <%s> but was <%s>", expected, actual.getStatus());
        }
        return this;
    }

    public NotificationAssert isTerminal() {
        isNotNull();
        if (!TERMINAL_STATUSES.contains(actual.getStatus())) {
            failWithMessage("Expected notification to be in a terminal status %s but was <%s>",
                    TERMINAL_STATUSES, actual.getStatus());
        }
        return this;
    }

    public NotificationAssert hasAttempts(int expected) {
        isNotNull();
        if (actual.getAttemptsCount() != expected) {
            failWithMessage("Expected attemptsCount <%s> but was <%s>", expected, actual.getAttemptsCount());
        }
        return this;
    }

    public NotificationAssert isLockedBy(String expectedWorker) {
        isNotNull();
        if (!Objects.equals(actual.getLockedBy(), expectedWorker)) {
            failWithMessage("Expected notification to be locked by <%s> but was <%s>",
                    expectedWorker, actual.getLockedBy());
        }
        return this;
    }
}
