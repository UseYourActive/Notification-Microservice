package bg.sit_varna.sit.si.controller.base;

import bg.sit_varna.sit.si.config.app.LocaleResolver;
import bg.sit_varna.sit.si.constant.WebhookProvider;
import bg.sit_varna.sit.si.dto.model.WebhookSignature;
import bg.sit_varna.sit.si.service.webhook.WebhookHeaderResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.logging.Logger;

import java.util.Locale;

public abstract class BaseResource {

    // Not final: Quarkus ArC requires a non-private no-args constructor on
    // this class and its 4 subclasses (NotificationResource, ChannelResource,
    // TemplateResource, WebhookResource) to generate a client proxy
    // (confirmed empirically - removing it fails deployment with
    // "unproxyable bean class"), and that constructor can't assign these.
    // Other single-class JAX-RS resources (e.g. MetricsResource) don't hit
    // this and stay fully constructor-injected with final fields - the
    // no-args-constructor requirement is specific to this abstract-base
    // hierarchy, not JAX-RS resources in general.
    protected LocaleResolver localeResolver;
    protected WebhookHeaderResolver headerResolver;

    // JAX-RS field injection (not CDI @Inject) - out of scope for the
    // constructor-injection sweep that converted everything else here.
    @Context
    protected HttpHeaders httpHeaders;

    @Inject
    protected BaseResource(LocaleResolver localeResolver, WebhookHeaderResolver headerResolver) {
        this.localeResolver = localeResolver;
        this.headerResolver = headerResolver;
    }

    protected BaseResource() {
    }

    protected abstract Logger getLogger();

    protected Locale resolveLocale() {
        return localeResolver.resolveLocale(httpHeaders);
    }

    protected WebhookSignature resolveSignature(WebhookProvider provider) {
        return headerResolver.resolve(httpHeaders, provider);
    }
}
