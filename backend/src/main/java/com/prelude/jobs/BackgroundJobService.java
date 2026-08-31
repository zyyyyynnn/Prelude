package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.BusinessException;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobRequested;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
import com.prelude.jobs.persistence.JobAttempt;
import com.prelude.jobs.persistence.JobAttemptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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

    private static final int STALE_RUNNING_MINUTES = 5;
    private static final int RECOVERY_BATCH = 100;

    private final BackgroundJobMapper jobMapper;
    private final JobAttemptMapper attemptMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IncompleteEventPublications incompleteEventPublications;

    public BackgroundJobService(
        BackgroundJobMapper jobMapper,
        JobAttemptMapper attemptMapper,
        ApplicationEventPublisher eventPublisher,
        IncompleteEventPublications incompleteEventPublications
    ) {
        this.jobMapper = jobMapper;
        this.attemptMapper = attemptMapper;
        this.eventPublisher = eventPublisher;
        this.incompleteEventPublications = incompleteEventPublications;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BackgroundJobRef request(BackgroundJobRequest request) {
        String operationKey = request.operationKey();
        BackgroundJob existing = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getOperationKey, operationKey)
            .last("LIMIT 1"));
        if (existing != null) {
            // Duplicate request: same logical job, no duplicate work.
            return new BackgroundJobRef(existing.getJobId(), existing.getStatus());
        }
        BackgroundJob job = new BackgroundJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setType(request.type());
        job.setAccountId(request.accountId());
        job.setSubjectId(request.subjectId());
        job.setOperationKey(operationKey);
        job.setPayloadJson(request.payloadJson());
        job.setStatus(BackgroundJob.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException race) {
            BackgroundJob winner = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
                .eq(BackgroundJob::getOperationKey, operationKey)
                .last("LIMIT 1"));
            return new BackgroundJobRef(winner.getJobId(), winner.getStatus());
        }
        eventPublisher.publishEvent(new BackgroundJobRequested(job.getJobId()));
        return new BackgroundJobRef(job.getJobId(), job.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClaimOutcome claim(String jobId) {
        BackgroundJob job = requireJob(jobId);
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .set(BackgroundJob::getAttemptCount, job.getAttemptCount() + 1)
            .set(BackgroundJob::getClaimedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.PENDING));
        if (updated != 1) {
            return ClaimOutcome.skip(job.getStatus(), "not pending");
        }
        BackgroundJob claimed = requireJob(jobId);
        JobAttempt attempt = new JobAttempt();
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(claimed.getAttemptCount());
        attempt.setStatus(JobAttempt.RUNNING);
        attempt.setStartedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);
        return ClaimOutcome.start();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(String jobId) {
        closeAttempt(jobId, JobAttempt.SUCCEEDED, null);
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.SUCCEEDED)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
        if (updated != 1) {
            log.warn("Job {} completed by a non-RUNNING claim path; state={}", jobId, requireJob(jobId).getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fail(String jobId, String sanitizedFailure) {
        BackgroundJob job = requireJob(jobId);
        closeAttempt(jobId, JobAttempt.FAILED, sanitizedFailure);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            // Bounded retry by the jobs executor: back to PENDING and dispatch again
            // through the reliable application-event path.
            jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
                .set(BackgroundJob::getStatus, BackgroundJob.PENDING)
                .set(BackgroundJob::getLastError, sanitizedFailure)
                .eq(BackgroundJob::getJobId, jobId)
                .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
            eventPublisher.publishEvent(new BackgroundJobRequested(jobId));
            return;
        }
        jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.FAILED)
            .set(BackgroundJob::getLastError, sanitizedFailure)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(String jobId, Long accountId) {
        BackgroundJob job = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
        if (job == null || !job.getAccountId().equals(accountId)) {
            // Cross-account cancels are not-found equivalent.
            throw BusinessException.notFound("任务不存在");
        }
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.CANCELLED)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.PENDING));
        return updated == 1;
    }

    @Override
    public BackgroundJobView view(String jobId, Long accountId) {
        BackgroundJob job = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
        if (job == null || !job.getAccountId().equals(accountId)) {
            throw BusinessException.notFound("任务不存在");
        }
        return toView(job);
    }

    @Override
    public BackgroundJobView dispatchedJob(String jobId) {
        return toView(requireJob(jobId));
    }

    private BackgroundJobView toView(BackgroundJob job) {
        return new BackgroundJobView(
            job.getJobId(), job.getType(), job.getSubjectId(), job.getAccountId(), job.getStatus(),
            job.getAttemptCount(), job.getMaxAttempts(), job.getLastError());
    }

    /**
     * Stale RUNNING recovery: a claim whose process died leaves RUNNING with
     * a dead attempt. The attempt is closed as INTERRUPTED and the job either
     * returns to PENDING (dispatched again via the application event) or
     * reaches its terminal FAILED state. Never a direct broker call.
     */
    @Scheduled(fixedDelayString = "${prelude.jobs.stale-recovery-delay-ms:60000}")
    public void recoverStaleRunning() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_RUNNING_MINUTES);
        var staleJobs = jobMapper.selectList(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .lt(BackgroundJob::getClaimedAt, cutoff)
            .last("LIMIT " + RECOVERY_BATCH));
        for (BackgroundJob job : staleJobs) {
            try {
                recoverOne(job);
            } catch (RuntimeException exception) {
                log.warn("Stale job recovery failed for {}; retrying next pass", job.getJobId(), exception);
            }
        }
        if (!staleJobs.isEmpty()) {
            log.info("Recovered {} stale RUNNING jobs", staleJobs.size());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    void recoverOne(BackgroundJob job) {
        JobAttempt latest = attemptMapper.selectOne(new LambdaQueryWrapper<JobAttempt>()
            .eq(JobAttempt::getJobId, job.getJobId())
            .orderByDesc(JobAttempt::getAttemptNumber)
            .last("LIMIT 1"));
        if (latest != null && JobAttempt.RUNNING.equals(latest.getStatus())) {
            latest.setStatus(JobAttempt.INTERRUPTED);
            latest.setFinishedAt(LocalDateTime.now());
            latest.setFailureSummary("worker interrupted before completion");
            attemptMapper.updateById(latest);
        }
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
                .set(BackgroundJob::getStatus, BackgroundJob.PENDING)
                .eq(BackgroundJob::getJobId, job.getJobId())
                .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
            if (updated == 1) {
                eventPublisher.publishEvent(new BackgroundJobRequested(job.getJobId()));
            }
            return;
        }
        jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.FAILED)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, job.getJobId())
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
    }

    /**
     * Incomplete publication recovery: resubmit through Spring Modulith's
     * official API. The framework owns the publication registry; this only
     * triggers it.
     */
    @Scheduled(fixedDelayString = "${prelude.jobs.publication-resubmission-delay-ms:120000}")
    public void resubmitIncompletePublications() {
        try {
            incompleteEventPublications
                .resubmitIncompletePublicationsOlderThan(java.time.Duration.ofMinutes(2));
        } catch (RuntimeException exception) {
            log.warn("Publication resubmission pass failed; retrying next cycle", exception);
        }
    }

    private void closeAttempt(String jobId, String status, String failureSummary) {
        JobAttempt latest = attemptMapper.selectOne(new LambdaQueryWrapper<JobAttempt>()
            .eq(JobAttempt::getJobId, jobId)
            .orderByDesc(JobAttempt::getAttemptNumber)
            .last("LIMIT 1"));
        if (latest != null && JobAttempt.RUNNING.equals(latest.getStatus())) {
            latest.setStatus(status);
            latest.setFinishedAt(LocalDateTime.now());
            latest.setFailureSummary(failureSummary);
            attemptMapper.updateById(latest);
        }
    }

    private BackgroundJob requireJob(String jobId) {
        BackgroundJob job = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
        if (job == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "job_not_found", "任务不存在");
        }
        return job;
    }
}
