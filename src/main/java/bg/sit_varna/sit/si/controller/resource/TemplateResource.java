package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.config.app.LocaleResolver;
import bg.sit_varna.sit.si.controller.api.TemplateApi;
import bg.sit_varna.sit.si.controller.base.BaseResource;
import bg.sit_varna.sit.si.dto.request.CreateTemplateRequest;
import bg.sit_varna.sit.si.dto.request.GetTemplatesRequest;
import bg.sit_varna.sit.si.dto.request.TemplateValidationRequest;
import bg.sit_varna.sit.si.dto.response.GetTemplatesResponse;
import bg.sit_varna.sit.si.dto.response.TemplateValidationResponse;
import bg.sit_varna.sit.si.dto.response.UpdateTemplateRequest;
import bg.sit_varna.sit.si.service.webhook.WebhookHeaderResolver;
import bg.sit_varna.sit.si.template.core.TemplateInfo;
import bg.sit_varna.sit.si.template.core.TemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TemplateResource extends BaseResource implements TemplateApi {

    private static final Logger LOG = Logger.getLogger(TemplateResource.class);

    // Not final - see BaseResource's no-args constructor comment.
    private TemplateService templateService;

    @Inject
    public TemplateResource(LocaleResolver localeResolver,
                            WebhookHeaderResolver headerResolver,
                            TemplateService templateService) {
        super(localeResolver, headerResolver);
        this.templateService = templateService;
    }

    protected TemplateResource() {
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    /**
     * GET /api/v1/templates/validate
     * Validate template existence
     */
    @Override
    public Response validateTemplate(@Valid @BeanParam TemplateValidationRequest request) {
        LOG.infof("Validating template: %s for locale: %s", request.getTemplate(), request.getLocale());

        boolean exists = templateService.templateExists(
                request.getTemplate(), request.getLocale());

        TemplateValidationResponse response = TemplateValidationResponse.of(
                request.getTemplate(),
                request.getLocale(),
                exists
        );

        return Response.ok(response).build();
    }

    /**
     * GET /api/v1/templates
     * Get available templates
     */
    @Override
    public Response getAvailableFileTemplates(@BeanParam GetTemplatesRequest request) {
        LOG.infof("Getting available templates with filters - type: %s, locale: %s",
                request.getType(), request.getLocale());

        List<TemplateInfo> templates = templateService.getAvailableFileTemplates();

        // Apply filters if provided
        if (request.getType() != null && !request.getType().isBlank()) {
            templates = templates.stream()
                    .filter(template -> template.getType().equalsIgnoreCase(request.getType()))
                    .collect(Collectors.toList());
        }

        if (request.getLocale() != null && !request.getLocale().isBlank()) {
            templates = templates.stream()
                    .filter(template -> template.getLocales().contains(request.getLocale()))
                    .collect(Collectors.toList());
        }

        GetTemplatesResponse response = GetTemplatesResponse.of(templates);
        return Response.ok(response).build();
    }

    @Override
    public Response createTemplate(CreateTemplateRequest request) {
        LOG.infof("Creating DB template: %s (%s)", request.templateName(), request.locale());
        return Response.status(Response.Status.CREATED)
                .entity(templateService.createTemplate(request))
                .build();
    }

    @Override
    public Response getAllDbTemplates() {
        return Response.ok(templateService.getAllDbTemplates()).build();
    }

    @Override
    public Response getTemplate(String id) {
        return Response.ok(templateService.getTemplate(UUID.fromString(id))).build();
    }

    @Override
    public Response updateTemplate(String id, UpdateTemplateRequest request) {
        LOG.infof("Updating DB template: %s", id);
        return Response.ok(templateService.updateTemplate(UUID.fromString(id), request)).build();
    }

    @Override
    public Response deleteTemplate(String id) {
        LOG.infof("Deleting DB template: %s", id);
        templateService.deleteTemplate(UUID.fromString(id));
        return Response.noContent().build();
    }
}
