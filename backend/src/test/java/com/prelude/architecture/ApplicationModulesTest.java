package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.PreludeApplication;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    private static final Set<String> EXPECTED_MODULES = Set.of(
        "identity", "settings", "llm", "tools", "context", "agent", "artifact", "assets",
        "jobs", "resume", "template", "documents", "interview", "voice", "activity", "telemetry"
    );

    @Test
    void verifiesTheSixteenModuleTopology() {
        var modules = ApplicationModules.of(PreludeApplication.class);

        modules.verify();

        var identifiers = StreamSupport.stream(modules.spliterator(), false)
            .map(module -> module.getIdentifier().toString())
            .collect(Collectors.toUnmodifiableSet());

        assertThat(identifiers).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
        assertThat(identifiers).doesNotContain("bootstrap", "data", "workspace");
    }
}
