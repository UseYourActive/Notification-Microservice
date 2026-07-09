package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the domain {@link Notification} dispatched for a new send request -
 * mints a fresh id per request (see NotificationService.persistRecord()'s
 * dedup guard, which relies on this id always being unique) and stamps the
 * resolved locale onto it.
 */
@ApplicationScoped
public class NotificationMapper {

    public Notification toDomain(SendNotificationRequest request, Locale locale) {
        if (request == null && locale == null) {
            return null;
        }

        Notification.Builder builder = Notification.builder();

        if (request != null) {
            builder.recipient(request.recipient());
            builder.channel(request.channel());
            builder.templateName(request.templateName());
            Map<String, Object> data = request.data();
            if (data != null) {
                builder.data(new LinkedHashMap<>(data));
            }
            builder.message(request.message());
        }
        builder.id(UUID.randomUUID().toString());
        builder.locale(locale.toLanguageTag());

        return builder.build();
    }
}
