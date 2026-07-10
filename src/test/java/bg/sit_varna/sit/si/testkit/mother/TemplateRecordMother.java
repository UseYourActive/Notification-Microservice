package bg.sit_varna.sit.si.testkit.mother;

import bg.sit_varna.sit.si.entity.TemplateRecord;

import java.util.UUID;

public final class TemplateRecordMother {

    private TemplateRecordMother() {
    }

    public static Builder aTemplate() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String templateName = "email/welcome";
        private String locale = "en";
        private String content = "<p>Hello!</p>";
        private boolean active = true;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withTemplateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder withLocale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder withContent(String content) {
            this.content = content;
            return this;
        }

        public Builder active() {
            this.active = true;
            return this;
        }

        public Builder inactive() {
            this.active = false;
            return this;
        }

        public TemplateRecord build() {
            TemplateRecord record = new TemplateRecord();
            record.setId(id);
            record.setTemplateName(templateName);
            record.setLocale(locale);
            record.setContent(content);
            record.setActive(active);
            return record;
        }
    }
}
