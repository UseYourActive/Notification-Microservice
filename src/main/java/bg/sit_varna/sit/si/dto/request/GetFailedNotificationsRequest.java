package bg.sit_varna.sit.si.dto.request;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Schema(description = "Query parameters for paginating failed notifications")
public final class GetFailedNotificationsRequest {

    @QueryParam("page")
    @DefaultValue("0")
    @Parameter(description = "Page number (0-based)", example = "0")
    private int page = 0;

    @QueryParam("size")
    @DefaultValue("20")
    @Parameter(description = "Page size (max 100)", example = "20")
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
