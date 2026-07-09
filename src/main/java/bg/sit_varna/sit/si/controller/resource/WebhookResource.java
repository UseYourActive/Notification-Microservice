package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.config.app.LocaleResolver;
import bg.sit_varna.sit.si.constant.WebhookProvider;
import bg.sit_varna.sit.si.controller.api.WebhookApi;
import bg.sit_varna.sit.si.controller.base.BaseResource;
import bg.sit_varna.sit.si.dto.model.WebhookSignature;
import bg.sit_varna.sit.si.service.webhook.WebhookHeaderResolver;
import bg.sit_varna.sit.si.service.webhook.WebhookService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebhookResource extends BaseResource implements WebhookApi {

    private static final Logger LOG = Logger.getLogger(WebhookResource.class);

    // Not final - see BaseResource's no-args constructor comment.
    private WebhookService webhookService;

    @Inject
    public WebhookResource(LocaleResolver localeResolver, WebhookHeaderResolver headerResolver,
                            WebhookService webhookService) {
        super(localeResolver, headerResolver);
        this.webhookService = webhookService;
    }

    protected WebhookResource() {
    }

    @Override
    public Response handleSendGridWebhook(String rawPayload) {
        WebhookSignature signatureData = headerResolver.resolve(httpHeaders, WebhookProvider.SENDGRID);

        try {
            webhookService.verifyAndProcess(signatureData.signature(), signatureData.timestamp(), rawPayload);
            return Response.ok().build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } catch (Exception e) {
            LOG.error("Unexpected error in webhook", e);
            return Response.ok().build();
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
