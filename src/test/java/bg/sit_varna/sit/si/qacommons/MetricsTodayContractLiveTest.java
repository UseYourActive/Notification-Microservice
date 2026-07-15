package bg.sit_varna.sit.si.qacommons;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires the real notification service running externally - see root
 * README. Run with {@code mvn test -DrunLive=true}.
 */
@Tag("live")
class MetricsTodayContractLiveTest {

    @Test
    void today_returnsEnvelopeContractShape() {
        // Arrange
        MetricsEndpoint endpoint = new MetricsEndpoint(QaConfig.fromEnv());

        // Act
        ApiResult<Map, ErrorResponse> result = endpoint.today();

        // Assert - structural only, MetricsResource has no typed DTO
        // (findings.md #3).
        assertThat(result.status()).isEqualTo(200);
        Map<?, ?> metrics = result.expectSuccess();
        assertThat(metrics.get("total")).isInstanceOf(Number.class);
        assertThat(metrics.get("successRate")).isInstanceOf(Number.class);
        assertThat(metrics.get("byChannel")).isInstanceOf(Map.class);
    }
}
