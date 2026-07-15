package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import bg.sit_varna.sit.si.dto.response.SendNotificationResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;

public final class NotificationsEndpoint
        extends Endpoint<SendNotificationRequest, SendNotificationResponse, ErrorResponse> {

    public NotificationsEndpoint(QaConfig config) {
        super(config, "/api/v1/notifications/send", SendNotificationResponse.class, ErrorResponse.class);
    }

    public ApiResult<SendNotificationResponse, ErrorResponse> send(SendNotificationRequest request) {
        return post(request);
    }
}
