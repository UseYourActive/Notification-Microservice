package bg.sit_varna.sit.si.template.engine;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class QuteTemplateEngine {

    private final Engine engine;

    @Inject
    public QuteTemplateEngine(Engine engine) {
        this.engine = engine;
    }

    public String compileAndRender(String rawTemplateContent, Map<String, Object> data) {
        Template t = engine.parse(rawTemplateContent);
        return t.data(data).render();
    }

    public void validateSyntax(String rawTemplateContent) {
        engine.parse(rawTemplateContent);
    }
}
