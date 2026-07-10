package bg.sit_varna.sit.si.template.loading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateFileParserTest {

    private final TemplateFileParser parser = new TemplateFileParser();

    @Test
    void extractBaseName_wellFormedPath_returnsNameWithoutLocaleOrExtension() {
        assertThat(parser.extractBaseName("email/welcome_en.html")).isEqualTo("email/welcome");
    }

    @Test
    void extractBaseName_noUnderscore_returnsNull() {
        assertThat(parser.extractBaseName("email/welcome.html")).isNull();
    }

    @Test
    void extractLocale_wellFormedPath_returnsLocaleSegment() {
        assertThat(parser.extractLocale("email/welcome_en.html")).isEqualTo("en");
    }

    @Test
    void extractLocale_noDotAfterUnderscore_returnsNull() {
        assertThat(parser.extractLocale("email/welcome_en")).isNull();
    }

    @Test
    void extractType_knownChannelPrefix_returnsFolderName() {
        assertThat(parser.extractType("email/welcome_en.html")).isEqualTo("email");
    }

    @Test
    void extractType_unknownPrefix_returnsUnknown() {
        assertThat(parser.extractType("unknown-folder/file_en.html")).isEqualTo("unknown");
    }
}
