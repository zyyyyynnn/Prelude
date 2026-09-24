package com.prelude.jobs;

import com.prelude.jobs.application.port.BackgroundJobStore;
import com.prelude.jobs.application.port.BackgroundJobStore.JobRow;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Transactional stale-claim recovery. The job CAS is won before the attempt
 * record is touched, so a normal completion that wins the race stays intact.
 */
@Service
@RequiredArgsConstructor
public class BackgroundJobRecoveryService {

    private static final String INTERRUPTED = "worker interrupted before completion";

    private final BackgroundJobStore jobStore;
    private final ApplicationEventPublisher eventPublisher;

    public List<String> findExpiredLeaseJobIds(LocalDateTime now, int limit) {
        return jobStore.findExpiredLeaseJobIds(now, limit);
    }

    @Transactional(rollbackFor = Exception.class)
    public RecoveryOutcome recover(String jobId, LocalDateTime now) {
        Optional<JobRow> found = jobStore.findByJobId(jobId);
        if (found.isEmpty()) {
            return RecoveryOutcome.NOT_STALE;
        }
        JobRow job = found.get();
        if (!BackgroundJobStore.RUNNING.equals(job.status())
            || job.leaseExpiresAt() == null || job.leaseExpiresAt().isAfter(now)) {
            return RecoveryOutcome.NOT_STALE;
        }

        boolean retry = job.attemptCount() < job.maxAttempts();
        if (jobStore.transitionStaleClaim(job, now, retry, INTERRUPTED) != 1) {
            return RecoveryOutcome.LOST_RACE;
        }

        jobStore.interruptRunningAttempt(jobId, job.attemptCount(), INTERRUPTED, LocalDateTime.now());

        if (retry) {
            eventPublisher.publishEvent(new BackgroundJobRequested(jobId));
            return RecoveryOutcome.RETRY_SCHEDULED;
        }
        eventPublisher.publishEvent(new BackgroundJobFailed(
            job.jobId(), job.type(), job.accountId(), job.subjectId(), INTERRUPTED));
        return RecoveryOutcome.TERMINAL_FAILED;
    }

    public enum RecoveryOutcome {
        RETRY_SCHEDULED,
        TERMINAL_FAILED,
        LOST_RACE,
        NOT_STALE
    }
}
