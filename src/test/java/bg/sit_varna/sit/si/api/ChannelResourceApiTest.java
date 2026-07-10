package bg.sit_varna.sit.si.api;

import bg.sit_varna.sit.si.testkit.base.ApiTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
class ChannelResourceApiTest extends ApiTestBase {

    @Test
    void getAvailableChannels_returnsAllThreeChannelsEnabled() {
        apiRequest()
                .when()
                .get("/api/v1/channels")
                .then()
                .statusCode(200)
                .body("total", equalTo(3))
                .body("channels.name", hasItems("EMAIL", "SMS", "TELEGRAM"))
                .body("channels.enabled", everyItem(equalTo(true)));
    }
}
