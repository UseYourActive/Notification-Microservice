package bg.sit_varna.sit.si.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Lets entity fields be typed as java.util.Locale while the column stays a plain
 * BCP-47 tag string (e.g. "en", "bg-BG") - matching how locale is represented
 * everywhere else in the app (Notification DTO, Locale.forLanguageTag() call sites).
 */
@Converter(autoApply = true)
public class LocaleAttributeConverter implements AttributeConverter<Locale, String> {

    @Override
    public String convertToDatabaseColumn(Locale locale) {
        return locale != null ? locale.toLanguageTag() : null;
    }

    @Override
    public Locale convertToEntityAttribute(String dbData) {
        return dbData != null ? Locale.forLanguageTag(dbData) : null;
    }
}
