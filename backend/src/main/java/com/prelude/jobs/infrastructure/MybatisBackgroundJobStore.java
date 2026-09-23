package com.prelude.jobs.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.jobs.application.port.BackgroundJobStore;
import com.prelude.jobs.application.port.BackgroundJobStore.ClaimResult;
import com.prelude.jobs.application.port.BackgroundJobStore.JobRow;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobEntity;
import com.prelude.jobs.infrastructure.persistence.BackgroundJobMapper;
import com.prelude.jobs.infrastructure.persistence.JobAttemptEntity;
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
        return jobMapper.selectList(new LambdaQueryWrapper<BackgroundJobEntity>()
                .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
                .le(BackgroundJobEntity::getLeaseExpiresAt, now)
                .last("LIMIT " + limit))
            .stream()
            .map(BackgroundJobEntity::getJobId)
            .toList();
    }

    @Override
    public JobRow insertPending(String type, Long accountId, Long subjectId, String operationKey, String payloadJson) {
        BackgroundJobEntity job = new BackgroundJobEntity();
        job.setJobId(UUID.randomUUID().toString());
        job.setType(type);
        job.setAccountId(accountId);
        job.setSubjectId(subjectId);
        job.setOperationKey(operationKey);
        job.setPayloadJson(payloadJson);
        job.setStatus(BackgroundJobEntity.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(BackgroundJobStore.DEFAULT_MAX_ATTEMPTS);
        jobMapper.insert(job);
        return toRow(job);
    }

    @Override
    public Optional<ClaimResult> claim(String jobId, LocalDateTime claimedAt, LocalDateTime leaseExpiresAt) {
        BackgroundJobEntity job = selectByJobId(jobId);
        if (job == null) {
            return Optional.empty();
        }
        int attemptNumber = attemptCountOf(job) + 1;
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJobEntity>()
            .set(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
            .set(BackgroundJobEntity::getAttemptCount, attemptNumber)
            .set(BackgroundJobEntity::getClaimedAt, claimedAt)
            .set(BackgroundJobEntity::getLeaseExpiresAt, leaseExpiresAt)
            .eq(BackgroundJobEntity::getJobId, jobId)
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.PENDING)
            .eq(BackgroundJobEntity::getAttemptCount, attemptCountOf(job)));
        if (updated != 1) {
            return Optional.empty();
        }
        JobAttemptEntity attempt = new JobAttemptEntity();
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(JobAttemptEntity.RUNNING);
        attempt.setStartedAt(claimedAt);
        attemptMapper.insert(attempt);
        return Optional.of(new ClaimResult(attemptNumber));
    }

    @Override
    public int renewLease(String jobId, int attemptNumber, LocalDateTime leaseExpiresAt) {
        return jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJobEntity>()
            .set(BackgroundJobEntity::getLeaseExpiresAt, leaseExpiresAt)
            .eq(BackgroundJobEntity::getJobId, jobId)
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
            .eq(BackgroundJobEntity::getAttemptCount, attemptNumber));
    }

    @Override
    public int complete(String jobId, int attemptNumber, LocalDateTime finishedAt) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJobEntity>()
            .set(BackgroundJobEntity::getStatus, BackgroundJobEntity.SUCCEEDED)
            .set(BackgroundJobEntity::getLeaseExpiresAt, null)
            .set(BackgroundJobEntity::getFinishedAt, finishedAt)
            .eq(BackgroundJobEntity::getJobId, jobId)
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
            .eq(BackgroundJobEntity::getAttemptCount, attemptNumber));
        if (updated == 1) {
            closeAttempt(jobId, attemptNumber, JobAttemptEntity.SUCCEEDED, null, finishedAt);
        }
        return updated;
    }

    @Override
    public int fail(String jobId, int attemptNumber, String sanitizedFailure, LocalDateTime finishedAt, boolean retry) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJobEntity>()
            .set(BackgroundJobEntity::getStatus, retry ? BackgroundJobEntity.PENDING : BackgroundJobEntity.FAILED)
            .set(BackgroundJobEntity::getLastError, sanitizedFailure)
            .set(BackgroundJobEntity::getClaimedAt, null)
            .set(BackgroundJobEntity::getLeaseExpiresAt, null)
            .set(BackgroundJobEntity::getFinishedAt, retry ? null : finishedAt)
            .eq(BackgroundJobEntity::getJobId, jobId)
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
            .eq(BackgroundJobEntity::getAttemptCount, attemptNumber));
        if (updated == 1) {
            closeAttempt(jobId, attemptNumber, JobAttemptEntity.FAILED, sanitizedFailure, finishedAt);
        }
        return updated;
    }

    @Override
    public int cancel(String jobId, LocalDateTime finishedAt) {
        return jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJobEntity>()
            .set(BackgroundJobEntity::getStatus, BackgroundJobEntity.CANCELLED)
            .set(BackgroundJobEntity::getFinishedAt, finishedAt)
            .eq(BackgroundJobEntity::getJobId, jobId)
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.PENDING));
    }

    @Override
    public int transitionStaleClaim(JobRow job, LocalDateTime now, boolean retry, String reason) {
        LambdaUpdateWrapper<BackgroundJobEntity> transition = new LambdaUpdateWrapper<BackgroundJobEntity>()
            .eq(BackgroundJobEntity::getJobId, job.jobId())
            .eq(BackgroundJobEntity::getStatus, BackgroundJobEntity.RUNNING)
            .eq(BackgroundJobEntity::getAttemptCount, job.attemptCount())
            .le(BackgroundJobEntity::getLeaseExpiresAt, now);
        if (retry) {
            transition
                .set(BackgroundJobEntity::getStatus, BackgroundJobEntity.PENDING)
                .set(BackgroundJobEntity::getClaimedAt, null)
                .set(BackgroundJobEntity::getLeaseExpiresAt, null)
                .set(BackgroundJobEntity::getLastError, reason);
        } else {
            transition
                .set(BackgroundJobEntity::getStatus, BackgroundJobEntity.FAILED)
                .set(BackgroundJobEntity::getLastError, reason)
                .set(BackgroundJobEntity::getLeaseExpiresAt, null)
                .set(BackgroundJobEntity::getFinishedAt, LocalDateTime.now());
        }
        return jobMapper.update(null, transition);
    }

    @Override
    public void interruptRunningAttempt(String jobId, int attemptNumber, String reason, LocalDateTime finishedAt) {
        closeAttempt(jobId, attemptNumber, JobAttemptEntity.INTERRUPTED, reason, finishedAt);
    }

    private void closeAttempt(String jobId, int attemptNumber, String status, String failureSummary, LocalDateTime finishedAt) {
        attemptMapper.update(null, new LambdaUpdateWrapper<JobAttemptEntity>()
            .set(JobAttemptEntity::getStatus, status)
            .set(JobAttemptEntity::getFinishedAt, finishedAt)
            .set(JobAttemptEntity::getFailureSummary, failureSummary)
            .eq(JobAttemptEntity::getJobId, jobId)
            .eq(JobAttemptEntity::getAttemptNumber, attemptNumber)
            .eq(JobAttemptEntity::getStatus, JobAttemptEntity.RUNNING));
    }

    private BackgroundJobEntity selectByOperationKey(String operationKey) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJobEntity>()
            .eq(BackgroundJobEntity::getOperationKey, operationKey)
            .last("LIMIT 1"));
    }

    private BackgroundJobEntity selectByJobId(String jobId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJobEntity>()
            .eq(BackgroundJobEntity::getJobId, jobId)
            .last("LIMIT 1"));
    }

    private Optional<JobRow> byJobId(BackgroundJobEntity job) {
        return job == null ? Optional.empty() : Optional.of(toRow(job));
    }

    private JobRow toRow(BackgroundJobEntity job) {
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

    private int attemptCountOf(BackgroundJobEntity job) {
        return job.getAttemptCount() == null ? 0 : job.getAttemptCount();
    }
}
