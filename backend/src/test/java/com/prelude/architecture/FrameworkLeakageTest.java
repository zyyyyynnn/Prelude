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

    private static String[] concat(String[] first, String[] second) {
        String[] merged = new String[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}
