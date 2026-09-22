package com.prelude.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.prelude")
class FrameworkLeakageTest {

    private static final String[] EXTERNAL_FRAMEWORKS = {
        "org.springframework.ai..",
        "org.bsc.langgraph4j..",
        "io.modelcontextprotocol..",
        "software.amazon.awssdk..",
    };

    private static final String[] PERSISTENCE_FRAMEWORKS = {
        "com.baomidou.mybatisplus..",
        "org.apache.ibatis..",
    };

    private static final String[] WEB_FRAMEWORKS = {
        "org.springframework.web..",
        "jakarta.servlet..",
        "javax.servlet..",
    };

    /** A domain model carrying an ORM annotation has stopped being the model. */
    @ArchTest
    static final ArchRule DOMAIN_STAYS_FRAMEWORK_FREE = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            concat(EXTERNAL_FRAMEWORKS, PERSISTENCE_FRAMEWORKS)
        );

    /** Ports and DTOs are the contract other layers implement; they cannot name a table. */
    @ArchTest
    static final ArchRule API_STAYS_PERSISTENCE_FREE = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORKS);

    /**
     * Use cases reach storage through a repository port. Holding a mapper and building a
     * query wrapper in the application layer leaves no seam to test the policy through.
     */
    @ArchTest
    static final ArchRule APPLICATION_STAYS_PERSISTENCE_FREE = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORKS);

    /**
     * The package-name rules above only reach classes that happen to sit in a
     * {@code persistence}/{@code application} package. A service or component at a module
     * root is invisible to them, which is how a mapper came to be injected straight into
     * {@code BackgroundJobService}. Judging by the stereotype instead of the package makes
     * the rule cover every bean that is meant to be a use case.
     */
    @ArchTest
    static final ArchRule SERVICE_BEANS_STAY_PERSISTENCE_FREE = noClasses()
        .that().areAnnotatedWith("org.springframework.stereotype.Service")
        .or().areAnnotatedWith("org.springframework.stereotype.Component")
        .and().resideOutsideOfPackages("..infrastructure..", "..persistence..", "..web..", "..api..")
        .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORKS);

    /**
     * The API surface is a contract: it names business types, never the transport that
     * happens to carry them. Nothing here has needed Spring MVC or the servlet API yet,
     * and this rule is what keeps the next controller from being written into it.
     */
    @ArchTest
    static final ArchRule API_STAYS_WEB_FREE = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAnyPackage(WEB_FRAMEWORKS);

    /**
     * A use case is transport-neutral: it cannot name a multipart upload, an HTTP session
     * or an SSE emitter. {@code StreamChatTurn} and {@code ListenInterview} used to build
     * and return the emitter themselves, which made them HTTP adapters; the emitter is now
     * constructed in {@code ..web..} and handed in.
     */
    @ArchTest
    static final ArchRule APPLICATION_STAYS_TRANSPORT_FREE = noClasses()
        .that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.web.multipart..",
            "jakarta.servlet.http..",
            "javax.servlet.http..",
            "org.springframework.web.servlet.mvc.method.annotation.SseEmitter"
        );

    private static String[] concat(String[] first, String[] second) {
        String[] merged = new String[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}
