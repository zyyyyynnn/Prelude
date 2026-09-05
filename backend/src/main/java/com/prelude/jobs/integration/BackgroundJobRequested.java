package com.prelude.jobs.integration;

import org.springframework.modulith.events.Externalized;

/**
 * Reliable background dispatch event. Published inside the business
 * transaction; Spring Modulith persists the publication and externalizes it
 * to RabbitMQ after commit. Recovery resubmission goes through the same
 * reliable path — never a direct RabbitTemplate.
 */
@Externalized("prelude.job.exchange::report.generate")
public record BackgroundJobRequested(String jobId) {
}
