package bg.sit_varna.sit.si.controller.api;

import bg.sit_varna.sit.si.dto.response.MetricsResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST API interface for notification metrics and analytics.
 *
 * <p>This interface provides endpoints to retrieve operational metrics about notification
 * delivery, including volume statistics, channel-specific breakdowns, and success rates.
 * These metrics are essential for monitoring system health, capacity planning, and
 * SLA compliance.</p>
 *
 * <p><strong>Metrics Collection:</strong></p>
 * <ul>
 *   <li>Metrics are collected in real-time as notifications are processed</li>
 *   <li>Data is stored in Redis for high-performance aggregation</li>
 *   <li>Metrics reset daily at midnight (UTC timezone)</li>
 *   <li>Historical metrics beyond today are not currently supported</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li>Real-time monitoring dashboards and operational visibility</li>
 *   <li>Alerting on delivery failures or degraded success rates</li>
 *   <li>Capacity planning and resource allocation decisions</li>
 *   <li>SLA reporting and compliance verification</li>
 *   <li>Channel performance comparison and optimization</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong></p>
 * <ul>
 *   <li>Metrics queries are highly optimized and execute in sub-millisecond time</li>
 *   <li>Safe to call frequently for real-time dashboard updates (every 5-30 seconds)</li>
 *   <li>No rate limiting applied to metrics endpoints</li>
 * </ul>
 *
 * @version 1.0
 * @since 1.0
 */
@Path("/api/v1/metrics")
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Metrics & Analytics",
        description = "Operational metrics, statistics, and performance analytics for notification delivery"
)
public interface MetricsApi {

    @GET
    @Path("/today")
    @Operation(
            summary = "Get today's notification metrics",
            description = """
            Returns comprehensive statistics and metrics for all notifications processed today.
            
            **Metric Categories:**
            
            1. **Volume Metrics:**
               - Total notification count across all channels
               - Per-channel breakdown showing distribution
            
            2. **Performance Metrics:**
               - Overall success rate percentage
               - Individual channel success rates (included in byChannel object)
            
            3. **Temporal Information:**
               - Metrics cover current day from 00:00:00 UTC
               - Timestamp of last metric update included
            
            **Response Structure:**
            ```json
            {
              "total": <number>,           // Total notifications sent today
              "byChannel": {               // Channel-specific breakdown
                "EMAIL": <number>,
                "SMS": <number>,
                "TELEGRAM": <number>
              },
              "successRate": <decimal>,    // Overall success rate (0-100%)
            }
            ```
            
            **Interpretation Guidelines:**
            
            - **High Volume:** Verify if expected; check for potential abuse
            - **Low Success Rate (<95%):** Investigate provider issues or configuration
            - **Channel Anomalies:** Significant deviation from baseline requires review
            - **Zero Metrics:** Normal early in day or during maintenance windows
            
            **Best Practices:**
            - Poll this endpoint at regular intervals (5-60 seconds) for real-time monitoring
            - Set up alerts for success rate drops below acceptable thresholds
            - Compare daily patterns to identify anomalies or trends
            - Use channel metrics to optimize routing and load distribution
            """
    )
    @APIResponses(value = {
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved today's metrics",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = MetricsResponse.class)
                    )
            )
    })
    Response getTodayMetrics();
}
