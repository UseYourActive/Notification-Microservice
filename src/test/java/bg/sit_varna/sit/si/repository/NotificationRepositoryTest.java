package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import io.quarkus.panache.common.Page;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
public class NotificationRepositoryTest extends BaseIntegrationTest {

    @Inject
    NotificationRepository notificationRepository;

    @Test
    @Transactional
    void testPersistAndFind() {
        String id = UUID.randomUUID().toString();
        NotificationRecord record = new NotificationRecord();
        record.setId(id);
        record.setRecipient("repo-test@example.com");
        record.setChannel(NotificationChannel.EMAIL);
        record.setStatus(NotificationStatus.QUEUED);
        record.setPayload(Map.of("key", "value"));

        notificationRepository.persist(record);

        NotificationRecord found = notificationRepository.findById(id);
        Assertions.assertNotNull(found);
        Assertions.assertEquals("repo-test@example.com", found.getRecipient());
        Assertions.assertEquals(NotificationStatus.QUEUED, found.getStatus());
    }

    @Test
    @Transactional
    void testFindByStatus() {
        NotificationRecord record = new NotificationRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRecipient("status-test@example.com");
        record.setChannel(NotificationChannel.SMS);
        record.setStatus(NotificationStatus.FAILED);
        notificationRepository.persist(record);

        List<NotificationRecord> failed = notificationRepository.findByStatus(NotificationStatus.FAILED);

        Assertions.assertFalse(failed.isEmpty());
        Assertions.assertTrue(failed.stream().anyMatch(r -> r.getRecipient().equals("status-test@example.com")));
    }

    @Test
    @Transactional
    void testFindByStatusPaged() {
        for (int i = 0; i < 3; i++) {
            NotificationRecord record = new NotificationRecord();
            record.setId(UUID.randomUUID().toString());
            record.setRecipient("paged-test-" + i + "@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.FAILED);
            notificationRepository.persist(record);
        }

        List<NotificationRecord> firstPage = notificationRepository
                .findByStatus(NotificationStatus.FAILED, Page.of(0, 2))
                .list();

        Assertions.assertEquals(2, firstPage.size());
        Assertions.assertTrue(firstPage.stream().allMatch(r -> r.getStatus() == NotificationStatus.FAILED));

        long total = notificationRepository.count("status", NotificationStatus.FAILED);
        Assertions.assertTrue(total >= 3);
    }
}
