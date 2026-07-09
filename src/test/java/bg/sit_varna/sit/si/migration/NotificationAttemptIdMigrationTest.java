package bg.sit_varna.sit.si.migration;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.repository.NotificationRepository;
import bg.sit_varna.sit.si.service.core.NotificationStateService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves V1.0.3 correctly fixes notification_attempts.id on a fresh
 * database, where Flyway (now that quarkus.flyway.locations is correct)
 * really does run V1.0.0 - which really does create id as uuid - for the
 * first time. The already-Hibernate-built-schema branch (every real
 * environment today, per investigation) is covered separately in
 * NotificationAttemptsMigrationReconciliationTest, which drives Flyway
 * directly against a hand-seeded "already bigint" schema.
 */
@QuarkusTest
class NotificationAttemptIdMigrationTest extends BaseIntegrationTest {

    @Inject
    NotificationStateService stateService;
    @Inject
    NotificationRepository notificationRepository;
    @Inject
    EntityManager em;

    @Test
    void allMigrationsApplyForRealOnAFreshDatabase() {
        List<?> rows = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(
                                "select version from flyway_schema_history where success = true order by installed_rank")
                        .getResultList());

        List<String> versions = rows.stream().map(Object::toString).toList();
        assertEquals(List.of("1.0.0", "1.0.1", "1.0.2", "1.0.3"), versions,
                "a fresh database has nothing to baseline, so Flyway must apply every migration for real");
    }

    @Test
    void notificationAttemptsIdColumnIsBigint() {
        Object[] column = (Object[]) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(
                                "select data_type, is_nullable from information_schema.columns " +
                                        "where table_name = 'notification_attempts' and column_name = 'id'")
                        .getSingleResult());

        assertEquals("bigint", column[0]);
        assertEquals("NO", column[1], "id must be NOT NULL - the primary key depends on it");
    }

    @Test
    void insertingAttemptsGeneratesDistinctNonNullLongIds() {
        String notificationId = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(notificationId);
            record.setRecipient("migration-test@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            record.setLocale(Locale.ENGLISH);
            notificationRepository.persist(record);
        });

        stateService.recordAttemptFailure(notificationId, "first attempt", null);
        stateService.recordAttemptFailure(notificationId, "second attempt", null);

        List<Long> ids = QuarkusTransaction.requiringNew().call(() ->
                notificationRepository.findById(notificationId).getAttempts().stream()
                        .map(attempt -> attempt.id)
                        .toList());

        assertEquals(2, ids.size());
        assertTrue(ids.stream().allMatch(Objects::nonNull), "every attempt must get a generated id");
        assertNotEquals(ids.get(0), ids.get(1), "each attempt must get its own distinct id");
    }
}
