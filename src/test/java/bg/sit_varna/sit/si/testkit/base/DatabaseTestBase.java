package bg.sit_varna.sit.si.testkit.base;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.entity.NotificationAttempt;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.entity.TemplateRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.junit.jupiter.api.AfterEach;

public abstract class DatabaseTestBase extends BaseIntegrationTest {

    @AfterEach
    void cleanDatabase() {
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationAttempt.deleteAll();
            NotificationRecord.deleteAll();
            TemplateRecord.deleteAll();
        });
    }
}
