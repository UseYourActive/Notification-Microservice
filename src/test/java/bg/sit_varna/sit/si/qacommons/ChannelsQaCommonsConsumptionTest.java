package bg.sit_varna.sit.si.qacommons;

import static org.assertj.core.api.Assertions.assertThat;

import bg.sit_varna.sit.si.dto.response.GetChannelsResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.core.config.QaConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Proves qa-commons-api is consumable from this repo purely as a JitPack
 * dependency (no local reactor reference) - not part of this repo's
 * existing testkit. Requires the service running locally on QA_BASE_URL /
 * localhost:8080 (see qa-commons's template/README.md for how to start it);
 * this repo has no existing tag-based exclusion for that, so a plain
 * {@code mvn test} will fail this test if the service isn't up.
 */
@Tag("live")
class ChannelsQaCommonsConsumptionTest {

    @Test
    void listChannels_returnsAllThreeChannelsEnabled() {
        ChannelsEndpoint endpoint = new ChannelsEndpoint(QaConfig.fromEnv());

        ApiResult<GetChannelsResponse, ErrorResponse> result = endpoint.list();

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.expectSuccess().total()).isEqualTo(3);
    }
}
