package com.prelude.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.prelude")
class FrameworkLeakageTest {

    @ArchTest
    static final ArchRule DOMAIN_STAYS_FRAMEWORK_FREE = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.ai..",
            "org.bsc.langgraph4j..",
            "io.modelcontextprotocol..",
            "software.amazon.awssdk.."
        )
        .allowEmptyShould(true);
}
