package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

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

    @Test
    void testClaimBatchNoDoubleClaimAcrossConcurrentCallers() throws Exception {
        List<String> ids = new ArrayList<>();
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 10; i++) {
                NotificationRecord record = new NotificationRecord();
                record.setId(UUID.randomUUID().toString());
                record.setRecipient("claim-test-" + i + "@example.com");
                record.setChannel(NotificationChannel.EMAIL);
                record.setStatus(NotificationStatus.QUEUED);
                notificationRepository.persist(record);
                ids.add(record.getId());
            }
        });

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<ClaimResult> claimedByWorker1 = Collections.synchronizedList(new ArrayList<>());
        List<ClaimResult> claimedByWorker2 = Collections.synchronizedList(new ArrayList<>());

        Runnable claimTask1 = () -> {
            awaitBarrier(barrier);
            QuarkusTransaction.requiringNew().run(() ->
                    claimedByWorker1.addAll(notificationRepository.claimBatch(6, "worker-1", 60)));
        };
        Runnable claimTask2 = () -> {
            awaitBarrier(barrier);
            QuarkusTransaction.requiringNew().run(() ->
                    claimedByWorker2.addAll(notificationRepository.claimBatch(6, "worker-2", 60)));
        };

        Thread t1 = new Thread(claimTask1);
        Thread t2 = new Thread(claimTask2);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Set<String> claimedIds1 = claimedByWorker1.stream()
                .map(r -> r.notification().getId()).collect(Collectors.toSet());
        Set<String> claimedIds2 = claimedByWorker2.stream()
                .map(r -> r.notification().getId()).collect(Collectors.toSet());

        Set<String> intersection = new java.util.HashSet<>(claimedIds1);
        intersection.retainAll(claimedIds2);
        Assertions.assertTrue(intersection.isEmpty(), "No row should be claimed by both workers");

        Set<String> combined = new java.util.HashSet<>(claimedIds1);
        combined.addAll(claimedIds2);
        combined.retainAll(ids);
        Assertions.assertEquals(10, combined.size(), "All 10 seeded rows should have been claimed exactly once between the two workers");
    }

    @Test
    void testClaimBatchReapsExpiredProcessingRow() {
        String id = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("stuck@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            record.setLockedBy("dead-worker");
            record.setLockedAt(LocalDateTime.now().minusSeconds(120));
            notificationRepository.persist(record);
        });

        List<ClaimResult> claimed = QuarkusTransaction.requiringNew().call(() ->
                notificationRepository.claimBatch(10, "worker-reaper", 60));

        ClaimResult result = claimed.stream()
                .filter(r -> r.notification().getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expired PROCESSING row was not reclaimed"));

        Assertions.assertTrue(result.reaped(), "Row reclaimed from PROCESSING should be tagged as reaped");
        Assertions.assertEquals("worker-reaper", result.notification().getLockedBy());
    }

    @Test
    void testClaimBatchDoesNotReapFreshProcessingRow() {
        String id = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationRecord record = new NotificationRecord();
            record.setId(id);
            record.setRecipient("in-flight@example.com");
            record.setChannel(NotificationChannel.EMAIL);
            record.setStatus(NotificationStatus.PROCESSING);
            record.setLockedBy("active-worker");
            record.setLockedAt(LocalDateTime.now());
            notificationRepository.persist(record);
        });

        List<ClaimResult> claimed = QuarkusTransaction.requiringNew().call(() ->
                notificationRepository.claimBatch(10, "worker-reaper", 60));

        Assertions.assertTrue(claimed.stream().noneMatch(r -> r.notification().getId().equals(id)),
                "A PROCESSING row within the visibility timeout must not be reclaimed");
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
