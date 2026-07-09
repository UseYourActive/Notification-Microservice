package bg.sit_varna.sit.si.mapper;

import bg.sit_varna.sit.si.dto.response.TemplateResponse;
import bg.sit_varna.sit.si.entity.TemplateRecord;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TemplateMapper {

    public TemplateResponse toResponse(TemplateRecord record) {
        if (record == null) {
            return null;
        }

        return new TemplateResponse(
                record.getId(),
                record.getTemplateName(),
                record.getLocale(),
                record.getContent(),
                record.isActive(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
