package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import java.util.Map;

/**
 * Typed against a raw {@link Map} rather than a production record -
 * {@code MetricsResource} has no typed DTO to type against. See
 * docs/specs/qa-commons-live-suite/findings.md #3.
 */
public final class MetricsEndpoint extends Endpoint<Void, Map, ErrorResponse> {

    public MetricsEndpoint(QaConfig config) {
        super(config, "/api/v1/metrics/today", Map.class, ErrorResponse.class);
    }

    public ApiResult<Map, ErrorResponse> today() {
        return get("");
    }
}
