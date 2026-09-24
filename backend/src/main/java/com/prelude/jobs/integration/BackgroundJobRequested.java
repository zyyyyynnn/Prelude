package com.prelude.jobs.integration;

import com.prelude.jobs.infrastructure.RabbitMqConfig;
import org.springframework.modulith.events.Externalized;

/**
 * Reliable background dispatch event. Published inside the business
 * transaction; Spring Modulith persists the publication and externalizes it
 * to RabbitMQ after commit. Recovery resubmission goes through the same
 * reliable path — never a direct RabbitTemplate.
 *
 * <p>The routing target is built from the same constants the broker topology
 * binds against, so the annotation and {@link RabbitMqConfig} cannot drift.
 */
@Externalized(RabbitMqConfig.EXCHANGE + "::" + JobTypes.REPORT_GENERATE)
public record BackgroundJobRequested(String jobId) {
}
