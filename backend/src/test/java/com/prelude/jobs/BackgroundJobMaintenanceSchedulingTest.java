package com.prelude.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recovery passes are the reliability contract for a job whose worker died mid-flight, and
 * they are only as real as the scheduling processor that runs them: an annotated method in a
 * bean nobody schedules reads fine in review and does nothing in production. This asserts the
 * registration, not the annotation — a dropped {@code @EnableScheduling} or a removed
 * {@code @Scheduled} fails here rather than surfacing as an unrecovered lease.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.jobs.scheduling-enabled=true",
    "prelude.jobs.report.consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackgroundJobMaintenanceSchedulingTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduling;

    @Test
    void bothMaintenancePassesAreScheduled() {
        var registered = scheduling.getScheduledTasks().stream()
            .map(task -> task.getTask())
            .map(String::valueOf)
            .toList();

        assertThat(registered)
            .as("stale-lease recovery and publication resubmission must both be scheduled")
            .contains(
                "com.prelude.jobs.infrastructure.BackgroundJobMaintenanceScheduler.recoverStaleRunning",
                "com.prelude.jobs.infrastructure.BackgroundJobMaintenanceScheduler.resubmitIncompletePublications");
    }
}
