package bg.sit_varna.sit.si.controller.resource;

import bg.sit_varna.sit.si.controller.api.MetricsApi;
import bg.sit_varna.sit.si.service.redis.MetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class MetricsResource implements MetricsApi {

    private final MetricsService metricsService;

    @Inject
    public MetricsResource(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public Response getTodayMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total", metricsService.getTodayTotal());
        metrics.put("byChannel", metricsService.getTodayByChannel());
        metrics.put("successRate", metricsService.getTodaySuccessRate());

        return Response.ok(metrics).build();
    }
}
