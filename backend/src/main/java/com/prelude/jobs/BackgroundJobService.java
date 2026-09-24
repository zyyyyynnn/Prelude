package com.prelude.jobs;

import com.prelude.BusinessException;
import com.prelude.jobs.application.port.BackgroundJobStore;
import com.prelude.jobs.application.port.BackgroundJobStore.JobRow;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobOperations.ExecutionLease;
import com.prelude.jobs.integration.BackgroundJobOperations.FailureOutcome;
import com.prelude.jobs.integration.BackgroundJobCancelled;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobRequested;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Durable background job execution: atomic claims, attempt records, bounded
 * retry, duplicate absorption by operationKey, cancellation races and stale
 * RUNNING recovery. Retry ownership lives here exclusively — domain handlers
 * never loop-retry. Dispatch events are Spring Modulith application events:
 * the publication persists in the requesting transaction and externalizes to
 * RabbitMQ after commit; recovery resubmission goes through the same event
 * path, never a direct RabbitTemplate.
 */
@Slf4j
@Service
public class BackgroundJobService implements BackgroundJobOperations {

    private final BackgroundJobStore jobStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService heartbeatExecutor;

    public BackgroundJobService(
        BackgroundJobStore jobStore,
        ApplicationEventPublisher eventPublisher,
        @Value("${prelude.jobs.lease-duration-seconds:120}") long leaseDurationSeconds,
        @Value("${prelude.jobs.heartbeat-interval-seconds:30}") long heartbeatIntervalSeconds
    ) {
        this.jobStore = jobStore;
        this.eventPublisher = eventPublisher;
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseDurationSeconds));
        long heartbeatSeconds = Math.max(1, heartbeatIntervalSeconds);
        if (heartbeatSeconds >= this.leaseDuration.toSeconds()) {
            throw new IllegalArgumentException("job heartbeat interval must be shorter than lease duration");
        }
        this.heartbeatInterval = Duration.ofSeconds(heartbeatSeconds);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "background-job-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BackgroundJobRef request(BackgroundJobRequest request) {
        Optional<JobRow> existing = jobStore.findByOperationKey(request.operationKey());
        if (existing.isPresent()) {
            // Duplicate request: same logical job, no duplicate work.
            return new BackgroundJobRef(existing.get().jobId(), existing.get().status());
        }
        JobRow job;
        try {
            job = jobStore.insertPending(
                request.type(), request.accountId(), request.subjectId(),
                request.operationKey(), request.payloadJson());
        } catch (DuplicateKeyException race) {
            JobRow winner = jobStore.findByOperationKey(request.operationKey())
                .orElseThrow(() -> new IllegalStateException(
                    "operation key " + request.operationKey() + " vanished after a duplicate-key race"));
            return new BackgroundJobRef(winner.jobId(), winner.status());
        }
        eventPublisher.publishEvent(new BackgroundJobRequested(job.jobId()));
        return new BackgroundJobRef(job.jobId(), job.status());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClaimOutcome claim(String jobId) {
        LocalDateTime claimedAt = LocalDateTime.now();
        Optional<BackgroundJobStore.ClaimResult> claimed =
            jobStore.claim(jobId, claimedAt, claimedAt.plus(leaseDuration));
        if (claimed.isEmpty()) {
            return ClaimOutcome.skip(requireJob(jobId).status(), "not pending");
        }
        return ClaimOutcome.start(claimed.get().attemptNumber());
    }

    @Override
    public boolean renewLease(String jobId, int attemptNumber) {
        return jobStore.renewLease(jobId, attemptNumber, LocalDateTime.now().plus(leaseDuration)) == 1;
    }

    @Override
    public ExecutionLease keepLeaseAlive(String jobId, int attemptNumber) {
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    renewLease(jobId, attemptNumber);
                } catch (RuntimeException exception) {
                    log.warn("Failed to renew lease for job {} attempt {}", jobId, attemptNumber, exception);
                }
            },
            heartbeatInterval.toMillis(),
            heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        return () -> heartbeat.cancel(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(String jobId, int attemptNumber) {
        JobRow job = requireJob(jobId);
        if (jobStore.complete(jobId, attemptNumber, LocalDateTime.now()) == 1) {
            eventPublisher.publishEvent(new BackgroundJobSucceeded(
                job.jobId(), job.type(), job.accountId(), job.subjectId()));
            return true;
        }
        log.info("Ignoring stale or duplicate completion for job {} attempt {}", jobId, attemptNumber);
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FailureOutcome fail(String jobId, int attemptNumber, Throwable failure) {
        String sanitizedFailure = JobFailureRedaction.sanitize(failure);
        JobRow job = requireJob(jobId);
        if (!BackgroundJobStore.RUNNING.equals(job.status()) || job.attemptCount() != attemptNumber) {
            return FailureOutcome.NOT_RUNNING;
        }
        boolean retry = job.attemptCount() < job.maxAttempts();
        if (jobStore.fail(jobId, attemptNumber, sanitizedFailure, LocalDateTime.now(), retry) != 1) {
            return FailureOutcome.NOT_RUNNING;
        }
        if (retry) {
            eventPublisher.publishEvent(new BackgroundJobRequested(jobId));
            return FailureOutcome.RETRY_SCHEDULED;
        }
        eventPublisher.publishEvent(new BackgroundJobFailed(
            job.jobId(), job.type(), job.accountId(), job.subjectId(), sanitizedFailure));
        return FailureOutcome.TERMINAL_FAILED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BackgroundJobView cancel(String jobId, Long accountId) {
        JobRow job = requireOwned(jobId, accountId);
        if (jobStore.cancel(jobId, LocalDateTime.now()) == 1) {
            eventPublisher.publishEvent(new BackgroundJobCancelled(
                job.jobId(), job.type(), job.accountId(), job.subjectId()));
        }
        return toView(requireJob(jobId));
    }

    @Override
    public BackgroundJobView view(String jobId, Long accountId) {
        return toView(requireOwned(jobId, accountId));
    }

    @Override
    public BackgroundJobView dispatchedJob(String jobId) {
        return toView(requireJob(jobId));
    }

    private BackgroundJobView toView(JobRow job) {
        return new BackgroundJobView(
            job.jobId(), job.type(), job.subjectId(), job.accountId(), job.status(),
            job.attemptCount(), job.maxAttempts(), job.lastError());
    }

    private JobRow requireOwned(String jobId, Long accountId) {
        JobRow job = requireJob(jobId);
        // Cross-account access is not-found equivalent.
        if (job.accountId() == null || !job.accountId().equals(accountId)) {
            throw BusinessException.notFound("任务不存在");
        }
        return job;
    }

    private JobRow requireJob(String jobId) {
        return jobStore.findByJobId(jobId)
            .orElseThrow(() -> BusinessException.jobNotFound("任务不存在"));
    }
}
