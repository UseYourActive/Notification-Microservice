package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.dto.response.FailedNotificationResponse;
import bg.sit_varna.sit.si.dto.response.PageResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;
import java.util.Map;

public final class FailedNotificationsEndpoint
        extends Endpoint<Void, PageResponse<FailedNotificationResponse>, ErrorResponse> {

    public FailedNotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/failed",
                new TypeReference<PageResponse<FailedNotificationResponse>>() {
                }, ErrorResponse.class);
    }

    public ApiResult<PageResponse<FailedNotificationResponse>, ErrorResponse> list(int page, int size) {
        return getWithQuery("", Map.of("page", page, "size", size));
    }
}
