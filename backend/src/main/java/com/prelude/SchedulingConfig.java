package com.prelude;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled maintenance passes — stale job-lease recovery and resubmission of
 * unfinished Modulith event publications. Without it {@code @Scheduled} methods are inert:
 * the beans register, the delays read from configuration, and nothing ever runs.
 *
 * <p>The default task scheduler is single-threaded, which is the intended shape here: a pass
 * must not overlap itself, and both passes are bounded queries. Both are also claim-checked —
 * recovery wins the lease transition as a compare-and-set, so a second instance finds nothing
 * to do rather than double-publishing. That makes the passes safe to leave enabled when the
 * deployment scales out; nothing in this application scales out today, and
 * {@code prelude.jobs.scheduling-enabled} is the switch that turns them off if a future one
 * needs a single designated runner.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
