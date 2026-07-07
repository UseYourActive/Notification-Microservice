package bg.sit_varna.sit.si.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of items")
public record PageResponse<T>(

        @Schema(description = "Items in the current page")
        List<T> items,

        @Schema(description = "Current page number (0-based)", example = "0")
        int page,

        @Schema(description = "Number of items per page", example = "20")
        int size,

        @Schema(description = "Total number of items across all pages", example = "42")
        long totalItems,

        @Schema(description = "Total number of pages", example = "3")
        int totalPages
) {
    public PageResponse {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }
}
