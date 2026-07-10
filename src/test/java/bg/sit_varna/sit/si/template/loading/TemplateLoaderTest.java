package bg.sit_varna.sit.si.template.loading;

import bg.sit_varna.sit.si.BaseIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TemplateLoaderTest extends BaseIntegrationTest {

    @Inject
    TemplateLoader templateLoader;

    @Test
    void templateExists_realTemplate_returnsTrue() {
        assertThat(templateLoader.templateExists("email/welcome_en.html")).isTrue();
    }

    @Test
    void templateExists_unknownTemplate_returnsFalse() {
        assertThat(templateLoader.templateExists("email/does-not-exist_en.html")).isFalse();
    }
}
