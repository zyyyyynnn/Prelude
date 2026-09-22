package com.prelude.jobs.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.jobs.application.port.BackgroundJobStore;
import com.prelude.jobs.application.port.BackgroundJobStore.ClaimResult;
import com.prelude.jobs.application.port.BackgroundJobStore.JobRow;
import com.prelude.jobs.infrastructure.persistence.BackgroundJob;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.jobs.infrastructure.persistence.JobAttempt;
import com.prelude.jobs.infrastructure.persistence.JobAttemptMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus adapter for {@link BackgroundJobStore}; the only place that names the
 * job and attempt mappers or builds a query wrapper.
 */
@Repository
@RequiredArgsConstructor
public class MybatisBackgroundJobStore implements BackgroundJobStore {

    private final BackgroundJobMapper jobMapper;
    private final JobAttemptMapper attemptMapper;

    @Override
    public Optional<JobRow> findByOperationKey(String operationKey) {
        return byJobId(selectByOperationKey(operationKey));
    }

    @Override
    public Optional<JobRow> findByJobId(String jobId) {
        return byJobId(selectByJobId(jobId));
    }

    @Override
    public List<String> findExpiredLeaseJobIds(LocalDateTime now, int limit) {
        return jobMapper.selectList(new LambdaQueryWrapper<BackgroundJob>()
                .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
                .le(BackgroundJob::getLeaseExpiresAt, now)
                .last("LIMIT " + limit))
            .stream()
            .map(BackgroundJob::getJobId)
            .toList();
    }

    @Override
    public JobRow insertPending(String type, Long accountId, Long subjectId, String operationKey, String payloadJson) {
        BackgroundJob job = new BackgroundJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setType(type);
        job.setAccountId(accountId);
        job.setSubjectId(subjectId);
        job.setOperationKey(operationKey);
        job.setPayloadJson(payloadJson);
        job.setStatus(BackgroundJob.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(BackgroundJobStore.DEFAULT_MAX_ATTEMPTS);
        jobMapper.insert(job);
        return toRow(job);
    }

    @Override
    public Optional<ClaimResult> claim(String jobId, LocalDateTime claimedAt, LocalDateTime leaseExpiresAt) {
        BackgroundJob job = selectByJobId(jobId);
        if (job == null) {
            return Optional.empty();
        }
        int attemptNumber = attemptCountOf(job) + 1;
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .set(BackgroundJob::getAttemptCount, attemptNumber)
            .set(BackgroundJob::getClaimedAt, claimedAt)
            .set(BackgroundJob::getLeaseExpiresAt, leaseExpiresAt)
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.PENDING)
            .eq(BackgroundJob::getAttemptCount, attemptCountOf(job)));
        if (updated != 1) {
            return Optional.empty();
        }
        JobAttempt attempt = new JobAttempt();
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(JobAttempt.RUNNING);
        attempt.setStartedAt(claimedAt);
        attemptMapper.insert(attempt);
        return Optional.of(new ClaimResult(attemptNumber));
    }

    @Override
    public int renewLease(String jobId, int attemptNumber, LocalDateTime leaseExpiresAt) {
        return jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getLeaseExpiresAt, leaseExpiresAt)
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .eq(BackgroundJob::getAttemptCount, attemptNumber));
    }

    @Override
    public int complete(String jobId, int attemptNumber, LocalDateTime finishedAt) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.SUCCEEDED)
            .set(BackgroundJob::getLeaseExpiresAt, null)
            .set(BackgroundJob::getFinishedAt, finishedAt)
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .eq(BackgroundJob::getAttemptCount, attemptNumber));
        if (updated == 1) {
            closeAttempt(jobId, attemptNumber, JobAttempt.SUCCEEDED, null, finishedAt);
        }
        return updated;
    }

    @Override
    public int fail(String jobId, int attemptNumber, String sanitizedFailure, LocalDateTime finishedAt, boolean retry) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, retry ? BackgroundJob.PENDING : BackgroundJob.FAILED)
            .set(BackgroundJob::getLastError, sanitizedFailure)
            .set(BackgroundJob::getClaimedAt, null)
            .set(BackgroundJob::getLeaseExpiresAt, null)
            .set(BackgroundJob::getFinishedAt, retry ? null : finishedAt)
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .eq(BackgroundJob::getAttemptCount, attemptNumber));
        if (updated == 1) {
            closeAttempt(jobId, attemptNumber, JobAttempt.FAILED, sanitizedFailure, finishedAt);
        }
        return updated;
    }

    @Override
    public int cancel(String jobId, LocalDateTime finishedAt) {
        return jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.CANCELLED)
            .set(BackgroundJob::getFinishedAt, finishedAt)
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.PENDING));
    }

    @Override
    public int transitionStaleClaim(JobRow job, LocalDateTime now, boolean retry, String reason) {
        LambdaUpdateWrapper<BackgroundJob> transition = new LambdaUpdateWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, job.jobId())
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .eq(BackgroundJob::getAttemptCount, job.attemptCount())
            .le(BackgroundJob::getLeaseExpiresAt, now);
        if (retry) {
            transition
                .set(BackgroundJob::getStatus, BackgroundJob.PENDING)
                .set(BackgroundJob::getClaimedAt, null)
                .set(BackgroundJob::getLeaseExpiresAt, null)
                .set(BackgroundJob::getLastError, reason);
        } else {
            transition
                .set(BackgroundJob::getStatus, BackgroundJob.FAILED)
                .set(BackgroundJob::getLastError, reason)
                .set(BackgroundJob::getLeaseExpiresAt, null)
                .set(BackgroundJob::getFinishedAt, LocalDateTime.now());
        }
        return jobMapper.update(null, transition);
    }

    @Override
    public void interruptRunningAttempt(String jobId, int attemptNumber, String reason, LocalDateTime finishedAt) {
        closeAttempt(jobId, attemptNumber, JobAttempt.INTERRUPTED, reason, finishedAt);
    }

    private void closeAttempt(String jobId, int attemptNumber, String status, String failureSummary, LocalDateTime finishedAt) {
        attemptMapper.update(null, new LambdaUpdateWrapper<JobAttempt>()
            .set(JobAttempt::getStatus, status)
            .set(JobAttempt::getFinishedAt, finishedAt)
            .set(JobAttempt::getFailureSummary, failureSummary)
            .eq(JobAttempt::getJobId, jobId)
            .eq(JobAttempt::getAttemptNumber, attemptNumber)
            .eq(JobAttempt::getStatus, JobAttempt.RUNNING));
    }

    private BackgroundJob selectByOperationKey(String operationKey) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getOperationKey, operationKey)
            .last("LIMIT 1"));
    }

    private BackgroundJob selectByJobId(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private Optional<JobRow> byJobId(BackgroundJob job) {
        return job == null ? Optional.empty() : Optional.of(toRow(job));
    }

    private JobRow toRow(BackgroundJob job) {
        return new JobRow(
            job.getJobId(),
            job.getType(),
            job.getAccountId(),
            job.getSubjectId(),
            job.getStatus(),
            attemptCountOf(job),
            job.getMaxAttempts() == null ? 0 : job.getMaxAttempts(),
            job.getLeaseExpiresAt(),
            job.getLastError()
        );
    }

    private int attemptCountOf(BackgroundJob job) {
        return job.getAttemptCount() == null ? 0 : job.getAttemptCount();
    }
}
