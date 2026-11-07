package app.bolo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** Layer rules carried over from medscribe-ai, plus Bolo's cross-module rule (architecture §3.3). */
class LayerRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.bolo");
    }

    @Test
    void domainDependsOnNothingOutsideItself() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..", "..config..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void applicationNeverDependsOnAdapters() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void servicesNeverTouchRepositories() {
        ArchRule rule = noClasses().that().areAnnotatedWith(Service.class)
                .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void onlySpeechAdaptersTouchTheAzureSdk() {
        noClasses().that().resideOutsideOfPackage("app.bolo.speech.adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("com.microsoft.cognitiveservices..")
                .check(classes);
    }

    @Test
    void onlyLlmAdaptersTouchTheAnthropicSdk() {
        noClasses().that().resideOutsideOfPackage("app.bolo.llm.adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
                .check(classes);
    }

    @Test
    void modulesMeetOnlyThroughInboundPorts() {
        List<String> violations = new ArrayList<>();
        classes.forEach(origin -> {
            String from = moduleOf(origin.getPackageName());
            if (from == null) return;
            origin.getDirectDependenciesFromSelf().forEach(dep -> {
                String targetPkg = dep.getTargetClass().getPackageName();
                String to = moduleOf(targetPkg);
                if (to == null || to.equals(from) || to.equals("common")) return;
                if (!targetPkg.contains(".application.port.in")) violations.add(dep.getDescription());
            });
        });
        assertThat(violations).as("cross-module dependencies outside port.in").isEmpty();
    }

    @Test
    void moduleOfNamesTheTopLevelPackage() {
        assertThat(moduleOf("app.bolo.learner.adapter.in.web")).isEqualTo("learner");
        assertThat(moduleOf("app.bolo.common")).isEqualTo("common");
        assertThat(moduleOf("app.bolo")).isNull();
        assertThat(moduleOf("java.util")).isNull();
    }

    /** Returns the module name for a package under app.bolo, or null for app.bolo itself and non-Bolo packages. */
    static String moduleOf(String pkg) {
        if (!pkg.startsWith("app.bolo.")) return null;
        String rest = pkg.substring("app.bolo.".length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
