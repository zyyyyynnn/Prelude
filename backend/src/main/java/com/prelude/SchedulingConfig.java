package com.prelude;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled maintenance passes — stale job-lease recovery and resubmission of
 * unfinished Modulith event publications. Without it {@code @Scheduled} methods are inert:
 * the beans register, the delays read from configuration, and nothing ever runs.
 *
 * <p>The default task scheduler is single-threaded, which is the intended shape here: a pass
 * must not overlap itself. Job recovery is bounded and claim-checked with compare-and-set.
 * Publication resubmission is bounded by batch size and maximum in-flight count, but relies on
 * the framework's in-process tracking; scaling out requires a single designated scheduler or a
 * database-backed claim mechanism. {@code prelude.jobs.scheduling-enabled} turns both passes off.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
