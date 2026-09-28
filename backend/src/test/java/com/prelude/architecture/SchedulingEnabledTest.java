package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A {@code @Scheduled} method on a bean is inert without the scheduling processor, and the
 * beans still construct — so the recovery passes can be dead while every test that calls them
 * directly stays green. This pins the switch that makes them live.
 */
class SchedulingEnabledTest {

    @Test
    void theApplicationEnablesScheduling() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EnableScheduling.class));

        assertThat(scanner.findCandidateComponents("com.prelude"))
            .as("a configuration under com.prelude must carry @EnableScheduling, or the "
                + "job-lease recovery and publication resubmission passes never run")
            .isNotEmpty();
    }
}
