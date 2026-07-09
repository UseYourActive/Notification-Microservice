package bg.sit_varna.sit.si.health;

import bg.sit_varna.sit.si.config.app.ApplicationConfig;
import bg.sit_varna.sit.si.constant.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class ApplicationInfoHealthCheck implements HealthCheck {

    private final ApplicationConfig applicationConfig;

    @Inject
    public ApplicationInfoHealthCheck(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("application-info")
                .up()
                .withData("name", applicationConfig.application().name())
                .withData("version", applicationConfig.application().version())
                .withData("channels", NotificationChannel.values().length)
                .withData("supportedLocales", applicationConfig.locales().size())
                .withData("locales", String.join(", ", applicationConfig.locales()))
                .build();
    }
}
