package bg.sit_varna.sit.si.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Unscoped (imports main AND test code) - "anywhere" means the whole
 * project, not just src/main.
 */
@AnalyzeClasses(packages = "bg.sit_varna.sit.si")
public class NoLombokOrMapstructArchTest {

    @ArchTest
    static final ArchRule NO_LOMBOK =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("lombok..")
                    .because("this project hand-writes constructors/getters/setters instead of generating "
                            + "them - explicit code is easier to review and debug");

    @ArchTest
    static final ArchRule NO_MAPSTRUCT =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("org.mapstruct..")
                    .because("mappers here are hand-written @ApplicationScoped components, not generated - "
                            + "see NotificationRecordMapper, NotificationMapper, TemplateMapper");
}
