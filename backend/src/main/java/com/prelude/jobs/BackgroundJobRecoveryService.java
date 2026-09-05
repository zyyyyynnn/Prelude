package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobRequested;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
import com.prelude.jobs.persistence.JobAttempt;
import com.prelude.jobs.persistence.JobAttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional stale-claim recovery. The job CAS is won before the attempt
 * record is touched, so a normal completion that wins the race stays intact.
 */
@Service
@RequiredArgsConstructor
public class BackgroundJobRecoveryService {

    private static final String INTERRUPTED = "worker interrupted before completion";

    private final BackgroundJobMapper jobMapper;
    private final JobAttemptMapper attemptMapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<String> findExpiredLeaseJobIds(LocalDateTime now, int limit) {
        return jobMapper.selectList(new LambdaQueryWrapper<BackgroundJob>()
                .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
                .le(BackgroundJob::getLeaseExpiresAt, now)
                .last("LIMIT " + limit))
            .stream()
            .map(BackgroundJob::getJobId)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RecoveryOutcome recover(String jobId, LocalDateTime now) {
        BackgroundJob job = jobMapper.selectOne(new LambdaQueryWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .last("LIMIT 1"));
        if (job == null || !BackgroundJob.RUNNING.equals(job.getStatus())
            || job.getLeaseExpiresAt() == null || job.getLeaseExpiresAt().isAfter(now)) {
            return RecoveryOutcome.NOT_STALE;
        }

        boolean retry = job.getAttemptCount() < job.getMaxAttempts();
        LambdaUpdateWrapper<BackgroundJob> transition = new LambdaUpdateWrapper<BackgroundJob>()
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING)
            .eq(BackgroundJob::getAttemptCount, job.getAttemptCount())
            .le(BackgroundJob::getLeaseExpiresAt, now);
        if (retry) {
            transition
                .set(BackgroundJob::getStatus, BackgroundJob.PENDING)
                .set(BackgroundJob::getClaimedAt, null)
                .set(BackgroundJob::getLeaseExpiresAt, null)
                .set(BackgroundJob::getLastError, INTERRUPTED);
        } else {
            transition
                .set(BackgroundJob::getStatus, BackgroundJob.FAILED)
                .set(BackgroundJob::getLastError, INTERRUPTED)
                .set(BackgroundJob::getLeaseExpiresAt, null)
                .set(BackgroundJob::getFinishedAt, LocalDateTime.now());
        }

        if (jobMapper.update(null, transition) != 1) {
            return RecoveryOutcome.LOST_RACE;
        }

        attemptMapper.update(null, new LambdaUpdateWrapper<JobAttempt>()
            .set(JobAttempt::getStatus, JobAttempt.INTERRUPTED)
            .set(JobAttempt::getFinishedAt, LocalDateTime.now())
            .set(JobAttempt::getFailureSummary, INTERRUPTED)
            .eq(JobAttempt::getJobId, jobId)
            .eq(JobAttempt::getAttemptNumber, job.getAttemptCount())
            .eq(JobAttempt::getStatus, JobAttempt.RUNNING));

        if (retry) {
            eventPublisher.publishEvent(new BackgroundJobRequested(jobId));
            return RecoveryOutcome.RETRY_SCHEDULED;
        }
        eventPublisher.publishEvent(new BackgroundJobFailed(
            job.getJobId(), job.getType(), job.getAccountId(), job.getSubjectId(), INTERRUPTED));
        return RecoveryOutcome.TERMINAL_FAILED;
    }

    public enum RecoveryOutcome {
        RETRY_SCHEDULED,
        TERMINAL_FAILED,
        LOST_RACE,
        NOT_STALE
    }
}
