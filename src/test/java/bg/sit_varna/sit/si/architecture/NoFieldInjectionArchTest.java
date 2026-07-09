package bg.sit_varna.sit.si.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.inject.Inject;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Scoped to production code only (DoNotIncludeTests) - @QuarkusTest classes
 * legitimately use @Inject on fields, that's the standard test-wiring
 * pattern and not what this rule bans.
 */
@AnalyzeClasses(packages = "bg.sit_varna.sit.si", importOptions = ImportOption.DoNotIncludeTests.class)
public class NoFieldInjectionArchTest {

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION_IN_MAIN =
            noFields()
                    .should().beAnnotatedWith(Inject.class)
                    .because("this project uses constructor injection exclusively - field injection hides "
                            + "a class's real dependencies and makes it harder to construct outside a CDI "
                            + "container (e.g. in a plain unit test)");
}
