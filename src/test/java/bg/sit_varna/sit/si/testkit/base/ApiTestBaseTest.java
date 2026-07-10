package bg.sit_varna.sit.si.testkit.base;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.testkit.assertions.ErrorResponseAssert;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
class ApiTestBaseTest extends ApiTestBase {

    @Test
    void apiRequest_andErrorResponseAssert_workTogetherAgainstAKnownValidationError() {
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL,
                "not-an-email",
                "email/welcome",
                null,
                Map.of()
        );

        var response = apiRequest()
                .body(request)
                .when()
                .post("/api/v1/notifications/send")
                .thenReturn();

        ErrorResponseAssert.assertThatError(response)
                .hasStatus(400)
                .hasCode("VALIDATION_FAILED")
                .hasDetailContaining("recipient");
    }
}
