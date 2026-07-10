package bg.sit_varna.sit.si.testkit.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Starts one WireMock instance before Quarkus boots (config is fixed at startup, so the
 * port must be known up front) and points all three channel-provider base-URLs at it.
 * Telegram/Twilio/SendGrid use disjoint path prefixes, so one shared instance is enough.
 */
public class WireMockLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    public static WireMockServer server() {
        return server;
    }

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();

        String hostPort = "localhost:" + server.port();

        return Map.of(
                "telegram.api.base-url", "http://" + hostPort + "/bot",
                "twilio.api.base-url", "http://" + hostPort,
                "sendgrid.api-base-url", hostPort
        );
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
