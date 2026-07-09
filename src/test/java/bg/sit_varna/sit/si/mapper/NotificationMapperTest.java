package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.constant.NotificationChannel;
import bg.sit_varna.sit.si.dto.model.Notification;
import bg.sit_varna.sit.si.dto.request.SendNotificationRequest;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain unit test - NotificationMapper has no CDI dependencies, so no
 * @QuarkusTest boot is needed.
 */
class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapper();

    @Test
    void toDomainCopiesEveryRequestFieldAndStampsIdAndLocale() {
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL,
                "mapper-test@example.com",
                "email/welcome",
                "hello there",
                Map.of("firstName", "Alex")
        );

        Notification notification = mapper.toDomain(request, Locale.forLanguageTag("bg"));

        assertNotNull(notification.getId());
        assertEquals(request.recipient(), notification.getRecipient());
        assertEquals(request.channel(), notification.getChannel());
        assertEquals(request.templateName(), notification.getTemplateName());
        assertEquals(request.message(), notification.getMessage());
        assertEquals(request.data(), notification.getData());
        assertEquals("bg", notification.getLocale());
    }

    @Test
    void toDomainMintsADistinctIdOnEveryCall() {
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, "id-test@example.com", null, "hi", Map.of());

        Notification first = mapper.toDomain(request, Locale.ENGLISH);
        Notification second = mapper.toDomain(request, Locale.ENGLISH);

        assertNotEquals(first.getId(), second.getId(),
                "the dedup guard on NotificationService.persistRecord() relies on every dispatch minting a fresh id");
        assertDoesNotThrow(() -> UUID.fromString(first.getId()));
    }

    @Test
    void toDomainReturnsNullOnlyWhenBothArgumentsAreNull() {
        assertNull(mapper.toDomain(null, null));
    }

    @Test
    void toDomainThrowsIfLocaleIsNullButRequestIsNot() {
        SendNotificationRequest request = new SendNotificationRequest(
                NotificationChannel.EMAIL, "npe-test@example.com", null, "hi", Map.of());

        // Matches the MapStruct-generated behavior being replaced: only the
        // both-null case was ever null-guarded, so a null locale with a
        // non-null request still NPEs on locale.toLanguageTag(). Every real
        // caller (NotificationResource) always resolves a non-null locale.
        assertThrows(NullPointerException.class, () -> mapper.toDomain(request, null));
    }
}
