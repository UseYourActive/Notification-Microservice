package bg.sit_varna.sit.si.testkit.mother;

import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationAttempt;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.entity.TemplateRecord;
import org.junit.jupiter.api.Test;

import static bg.sit_varna.sit.si.testkit.assertions.NotificationAssert.assertThatNotification;
import static bg.sit_varna.sit.si.testkit.mother.NotificationAttemptMother.anAttempt;
import static bg.sit_varna.sit.si.testkit.mother.NotificationRecordMother.aNotification;
import static bg.sit_varna.sit.si.testkit.mother.TemplateRecordMother.aTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class ObjectMothersTest {

    @Test
    void aNotification_appliesSensibleDefaults() {
        NotificationRecord notification = aNotification().build();

        assertThatNotification(notification).hasStatus(NotificationStatus.QUEUED);
        assertThat(notification.getRecipient()).isNotBlank();
    }

    @Test
    void aNotification_failedWithAttempts_isTerminalAndTracksAttemptCount() {
        NotificationRecord notification = aNotification().failed().withAttempts(3).build();

        assertThatNotification(notification)
                .isTerminal()
                .hasStatus(NotificationStatus.FAILED)
                .hasAttempts(3);
    }

    @Test
    void anAttempt_linksBackToItsNotification() {
        NotificationRecord notification = aNotification().build();

        NotificationAttempt attempt = anAttempt()
                .forNotification(notification)
                .withErrorMessage("provider timeout")
                .build();

        assertThat(attempt.getNotification()).isSameAs(notification);
        assertThat(attempt.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(attempt.getErrorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void aTemplate_appliesSensibleDefaults() {
        TemplateRecord template = aTemplate().build();

        assertThat(template.getId()).isNotNull();
        assertThat(template.isActive()).isTrue();
        assertThat(template.getContent()).isNotBlank();
    }
}
