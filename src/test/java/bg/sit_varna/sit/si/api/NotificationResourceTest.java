package bg.sit_varna.sit.si.api;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.entity.NotificationRecord;
import bg.sit_varna.sit.si.service.core.NotificationService;
import bg.sit_varna.sit.si.testkit.base.ApiTestBase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static bg.sit_varna.sit.si.testkit.assertions.ErrorResponseAssert.assertThatError;
import static bg.sit_varna.sit.si.testkit.mother.NotificationRecordMother.aNotification;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@QuarkusTest
public class NotificationResourceTest extends ApiTestBase {

    @InjectMock
    NotificationService notificationService;

    @Test
    void testSendEndpoint_ValidationFailure() {
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL,
                "bad-email",
                "email/welcome",
                null,
                Map.of()
        );

        var response = apiRequest()
                .body(request)
                .when()
                .post("/api/v1/notifications/send")
                .thenReturn();

        assertThatError(response)
                .hasStatus(400)
                .hasCode("VALIDATION_FAILED")
                .hasDetailContaining("recipient");
    }

    @Test
    void testSendEndpoint_Success() {
        Mockito.doNothing().when(notificationService).dispatchNotification(any());

        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL,
                "good@email.com",
                "email/welcome",
                null,
                Map.of("name", "Test")
        );

        apiRequest()
                .body(request)
                .when()
                .post("/api/v1/notifications/send")
                .then()
                .statusCode(202)
                .body("status", equalTo("QUEUED"));
    }

    @Test
    void testListFailedNotifications_ReturnsPagedResults() {
        NotificationRecord record = aNotification()
                .withId("550e8400-e29b-41d4-a716-446655440000")
                .withRecipient("failed@example.com")
                .withChannel(NotificationChannel.EMAIL)
                .withTemplateName("email/welcome")
                .failed()
                .build();
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        Mockito.when(notificationService.getFailedNotifications(anyInt(), anyInt()))
                .thenReturn(List.of(record));
        Mockito.when(notificationService.countFailedNotifications())
                .thenReturn(1L);

        apiRequest()
                .queryParam("page", 0)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/notifications/failed")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalItems", equalTo(1))
                .body("totalPages", equalTo(1))
                .body("items[0].notificationId", equalTo("550e8400-e29b-41d4-a716-446655440000"))
                .body("items[0].status", equalTo("FAILED"));
    }

    @Test
    void testListFailedNotifications_ClampsOversizedPageSize() {
        Mockito.when(notificationService.getFailedNotifications(anyInt(), anyInt()))
                .thenReturn(List.of());
        Mockito.when(notificationService.countFailedNotifications())
                .thenReturn(0L);

        apiRequest()
                .queryParam("size", 500)
                .when()
                .get("/api/v1/notifications/failed")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}
