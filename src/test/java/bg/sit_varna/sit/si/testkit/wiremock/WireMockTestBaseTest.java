package bg.sit_varna.sit.si.testkit.wiremock;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class WireMockTestBaseTest extends WireMockTestBase {

    @Inject
    @ConfigProperty(name = "twilio.api.base-url")
    String twilioApiBaseUrl;

    @Test
    void configOverridePointsAtWireMock() throws Exception {
        wireMock().stubFor(get(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(200).withBody("pong")));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(twilioApiBaseUrl + "/ping")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("pong");
    }
}
