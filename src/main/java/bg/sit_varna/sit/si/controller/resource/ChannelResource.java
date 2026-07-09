package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.config.app.LocaleResolver;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.controller.api.ChannelApi;
import bg.sit_varna.sit.si.controller.base.BaseResource;
import bg.sit_varna.sit.si.dto.response.GetChannelsResponse;
import bg.sit_varna.sit.si.service.webhook.WebhookHeaderResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ChannelResource extends BaseResource implements ChannelApi {

    private static final Logger LOG = Logger.getLogger(ChannelResource.class);

    @Inject
    public ChannelResource(LocaleResolver localeResolver, WebhookHeaderResolver headerResolver) {
        super(localeResolver, headerResolver);
    }

    protected ChannelResource() {
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    /**
     * GET /api/v1/channels
     * Get available notification channels
     */
    @Override
    public Response getAvailableChannels() {
        LOG.debug("Getting available notification channels");

        List<GetChannelsResponse.ChannelInfo> channelInfos = new ArrayList<>();

        for (NotificationChannel channel : NotificationChannel.values()) {
            channelInfos.add(new GetChannelsResponse.ChannelInfo(
                    channel.name(),
                    getChannelDescription(channel),
                    true
            ));
        }

        GetChannelsResponse response = GetChannelsResponse.of(channelInfos);
        return Response.ok(response).build();
    }

    private String getChannelDescription(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> "Send notifications via email";
            case SMS -> "Send notifications via SMS";
            case TELEGRAM -> "Send notifications via Telegram";
        };
    }
}
