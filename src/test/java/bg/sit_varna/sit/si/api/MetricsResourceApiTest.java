package bg.sit_varna.sit.si.api;

import bg.sit_varna.sit.si.testkit.base.ApiTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class MetricsResourceApiTest extends ApiTestBase {

    @Test
    void getTodayMetrics_returnsVolumeAndSuccessRateStructure() {
        // Redis is shared across test classes in this suite, so other tests may have
        // already recorded metrics - assert structure/ranges, not exact counts.
        Response response = apiRequest()
                .when()
                .get("/api/v1/metrics/today")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getLong("total")).isGreaterThanOrEqualTo(0);
        assertThat(response.jsonPath().getDouble("successRate")).isBetween(0.0, 100.0);
        assertThat(response.jsonPath().getMap("byChannel")).isNotNull();
    }
}
