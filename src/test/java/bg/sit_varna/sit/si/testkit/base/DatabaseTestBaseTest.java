package bg.sit_varna.sit.si.testkit.base;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationAttempt;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseTestBaseTest extends DatabaseTestBase {

    @Test
    @Order(1)
    void firstTest_seedsParentAndChildRows() {
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord notification = new NotificationRecord();
            notification.setId(UUID.randomUUID().toString());
            notification.setRecipient("leak-check@example.com");
            notification.setChannel(NotificationChannel.EMAIL);
            notification.setStatus(NotificationStatus.SENT);
            NotificationRecord.persist(notification);

            NotificationAttempt attempt = new NotificationAttempt();
            attempt.setNotification(notification);
            attempt.setStatus(NotificationStatus.SENT);
            NotificationAttempt.persist(attempt);
        });

        assertThat(NotificationRecord.count()).isEqualTo(1);
        assertThat(NotificationAttempt.count()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void secondTest_startsWithNoRowsLeakedFromFirstTest() {
        assertThat(NotificationRecord.count()).isZero();
        assertThat(NotificationAttempt.count()).isZero();
    }
}
