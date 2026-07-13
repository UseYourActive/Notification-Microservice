package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.dto.response.GetChannelsResponse;
import bg.sit_varna.sit.si.exception.exceptions.ErrorResponse;
import dev.qacommons.api.ApiResult;
import dev.qacommons.api.Endpoint;
import dev.qacommons.core.config.QaConfig;

public final class ChannelsEndpoint extends Endpoint<Void, GetChannelsResponse, ErrorResponse> {

    public ChannelsEndpoint(QaConfig config) {
        super(config, "/api/v1/channels", GetChannelsResponse.class, ErrorResponse.class);
    }

    public ApiResult<GetChannelsResponse, ErrorResponse> list() {
        return get("");
    }
}
