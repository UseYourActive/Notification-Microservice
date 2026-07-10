package bg.sit_varna.sit.si.repository;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.constant.NotificationStatus;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.testkit.base.DatabaseTestBase;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

import static bg.sit_varna.sit.si.testkit.mother.NotificationRecordMother.aNotification;

@QuarkusTest
public class NotificationRepositoryTest extends DatabaseTestBase {

    @Inject
    NotificationRepository notificationRepository;

    @Test
    @Transactional
    void testPersistAndFind() {
        NotificationRecord record = aNotification()
                .withRecipient("repo-test@example.com")
                .withChannel(NotificationChannel.EMAIL)
                .queued()
                .build();

        notificationRepository.persist(record);

        NotificationRecord found = notificationRepository.findById(record.getId());
        Assertions.assertNotNull(found);
        Assertions.assertEquals("repo-test@example.com", found.getRecipient());
        Assertions.assertEquals(NotificationStatus.QUEUED, found.getStatus());
    }

    @Test
    @Transactional
    void testFindByStatus() {
        NotificationRecord record = aNotification()
                .withRecipient("status-test@example.com")
                .withChannel(NotificationChannel.SMS)
                .failed()
                .build();
        notificationRepository.persist(record);

        List<NotificationRecord> failed = notificationRepository.findByStatus(NotificationStatus.FAILED);

        Assertions.assertFalse(failed.isEmpty());
        Assertions.assertTrue(failed.stream().anyMatch(r -> r.getRecipient().equals("status-test@example.com")));
    }

    @Test
    @Transactional
    void testFindByStatusPaged() {
        for (int i = 0; i < 3; i++) {
            NotificationRecord record = aNotification()
                    .withRecipient("paged-test-" + i + "@example.com")
                    .withChannel(NotificationChannel.EMAIL)
                    .failed()
                    .build();
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
                NotificationRecord record = aNotification()
                        .withRecipient("claim-test-" + i + "@example.com")
                        .withChannel(NotificationChannel.EMAIL)
                        .queued()
                        .build();
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
        NotificationRecord seed = aNotification()
                .withRecipient("stuck@example.com")
                .withChannel(NotificationChannel.EMAIL)
                .processing()
                .lockedBy("dead-worker")
                .build();
        seed.setLockedAt(LocalDateTime.now().minusSeconds(120));
        String id = seed.getId();
        QuarkusTransaction.requiringNew().run(() -> notificationRepository.persist(seed));

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
        NotificationRecord seed = aNotification()
                .withRecipient("in-flight@example.com")
                .withChannel(NotificationChannel.EMAIL)
                .processing()
                .lockedBy("active-worker")
                .build();
        String id = seed.getId();
        QuarkusTransaction.requiringNew().run(() -> notificationRepository.persist(seed));

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
