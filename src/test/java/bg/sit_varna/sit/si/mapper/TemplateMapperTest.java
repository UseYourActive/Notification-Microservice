package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.dto.response.TemplateResponse;
import bg.sit_varna.sit.si.entity.TemplateRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit test - TemplateMapper has no CDI dependencies, so no
 * @QuarkusTest boot is needed.
 */
class TemplateMapperTest {

    private final TemplateMapper mapper = new TemplateMapper();

    @Test
    void toResponseCopiesEveryField() {
        TemplateRecord record = new TemplateRecord();
        record.setId(UUID.randomUUID());
        record.setTemplateName("email/welcome");
        record.setLocale("en");
        record.setContent("<html>Hello</html>");
        record.setActive(true);
        record.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        record.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 11, 0));

        TemplateResponse response = mapper.toResponse(record);

        assertEquals(record.getId(), response.id());
        assertEquals(record.getTemplateName(), response.templateName());
        assertEquals(record.getLocale(), response.locale());
        assertEquals(record.getContent(), response.content());
        assertEquals(record.isActive(), response.active());
        assertEquals(record.getCreatedAt(), response.createdAt());
        assertEquals(record.getUpdatedAt(), response.updatedAt());
    }

    @Test
    void toResponseReturnsNullForNullRecord() {
        assertNull(mapper.toResponse(null));
    }
}
