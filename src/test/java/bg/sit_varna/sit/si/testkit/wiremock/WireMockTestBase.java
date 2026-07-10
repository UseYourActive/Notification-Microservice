package bg.sit_varna.sit.si.testkit.wiremock;

import bg.sit_varna.sit.si.testkit.base.DatabaseTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTestResource(WireMockLifecycleManager.class)
public abstract class WireMockTestBase extends DatabaseTestBase {

    @BeforeEach
    void resetWireMock() {
        wireMock().resetAll();
    }

    protected static WireMockServer wireMock() {
        return WireMockLifecycleManager.server();
    }
}
