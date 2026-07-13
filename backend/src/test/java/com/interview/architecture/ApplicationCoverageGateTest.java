package com.interview.architecture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationCoverageGateTest {

    @Test
    void mavenBlocksEachCoreApplicationPackageBelowSeventyPercent() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);

        assertThat(pom)
            .contains("<id>check-core-application-coverage</id>")
            .contains("<goal>check</goal>")
            .contains("<element>PACKAGE</element>")
            .contains("<include>com.interview.interview.application</include>")
            .contains("<include>com.interview.resume.application</include>")
            .contains("<include>com.interview.insight.application</include>")
            .contains("<counter>INSTRUCTION</counter>")
            .contains("<minimum>0.70</minimum>");
    }
}
