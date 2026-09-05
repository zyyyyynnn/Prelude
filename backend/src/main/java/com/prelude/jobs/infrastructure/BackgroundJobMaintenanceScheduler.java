package com.prelude.jobs.infrastructure;

import com.prelude.jobs.BackgroundJobRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/** Runtime-only orchestration for durable job and publication recovery. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "prelude.jobs", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobMaintenanceScheduler {

    private static final int RECOVERY_BATCH = 100;

    private final BackgroundJobRecoveryService recoveryService;
    private final IncompleteEventPublications incompleteEventPublications;

    @Scheduled(fixedDelayString = "${prelude.jobs.stale-recovery-delay-ms:60000}")
    public void recoverStaleRunning() {
        LocalDateTime now = LocalDateTime.now();
        for (String jobId : recoveryService.findExpiredLeaseJobIds(now, RECOVERY_BATCH)) {
            try {
                recoveryService.recover(jobId, now);
            } catch (RuntimeException exception) {
                log.warn("Stale job recovery failed for {}; retrying next pass", jobId, exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${prelude.jobs.publication-resubmission-delay-ms:120000}")
    public void resubmitIncompletePublications() {
        try {
            incompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(2));
        } catch (RuntimeException exception) {
            log.warn("Publication resubmission pass failed; retrying next cycle", exception);
        }
    }
}
