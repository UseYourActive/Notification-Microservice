package bg.sit_varna.sit.si.dto.response;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Today's notification metrics")
public record MetricsResponse(

        @Schema(description = "Total notifications sent today", example = "142")
        long total,

        @Schema(description = "Per-channel notification counts for today",
                example = "{\"EMAIL\":100,\"SMS\":30,\"TELEGRAM\":12}")
        Map<String, Long> byChannel,

        @Schema(description = "Overall success rate today, 0-100", example = "98.5")
        double successRate
) {
    public MetricsResponse {
        if (byChannel != null) {
            byChannel = Map.copyOf(byChannel);
        }
    }
}
