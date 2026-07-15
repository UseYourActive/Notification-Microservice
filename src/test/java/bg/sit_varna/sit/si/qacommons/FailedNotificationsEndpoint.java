package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import java.util.Map;

public final class FailedNotificationsEndpoint
        extends Endpoint<Void, FailedNotificationsPageResponse, ErrorResponse> {

    public FailedNotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/failed", FailedNotificationsPageResponse.class, ErrorResponse.class);
    }

    public ApiResult<FailedNotificationsPageResponse, ErrorResponse> list(int page, int size) {
        return getWithQuery("", Map.of("page", page, "size", size));
    }
}
