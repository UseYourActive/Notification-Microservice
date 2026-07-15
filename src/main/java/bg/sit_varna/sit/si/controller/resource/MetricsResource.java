package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.controller.api.MetricsApi;
import bg.sit_varna.sit.si.dto.response.MetricsResponse;
import bg.sit_varna.sit.si.service.redis.MetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class MetricsResource implements MetricsApi {

    private final MetricsService metricsService;

    @Inject
    public MetricsResource(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public Response getTodayMetrics() {
        MetricsResponse metrics = new MetricsResponse(
                metricsService.getTodayTotal(),
                metricsService.getTodayByChannel(),
                metricsService.getTodaySuccessRate());

        return Response.ok(metrics).build();
    }
}
