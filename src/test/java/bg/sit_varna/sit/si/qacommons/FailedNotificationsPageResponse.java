package bg.sit_varna.sit.si.qacommons;

import bg.sit_varna.sit.si.dto.response.FailedNotificationResponse;
import java.util.List;

/**
 * Concrete stand-in for the production {@code PageResponse<FailedNotificationResponse>}
 * envelope. qa-commons' {@code Endpoint} only accepts a raw {@code Class<TRes>},
 * and Jackson's {@code readValue(String, Class<T>)} erases {@code T} - deserializing
 * straight into {@code PageResponse.class} would silently produce
 * {@code items: List<LinkedHashMap>} instead of
 * {@code List<FailedNotificationResponse>}. Same field set as the real
 * envelope, reusing the real item type - see
 * docs/specs/qa-commons-live-suite/findings.md #2.
 */
public record FailedNotificationsPageResponse(
        List<FailedNotificationResponse> items, int page, int size, long totalItems, int totalPages) {
}
