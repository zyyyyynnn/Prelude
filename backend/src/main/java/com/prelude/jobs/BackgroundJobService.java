package com.prelude.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prelude.BusinessException;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobOperations.FailureOutcome;
import com.prelude.jobs.integration.BackgroundJobFailed;
import com.prelude.jobs.integration.BackgroundJobRequested;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import com.prelude.jobs.persistence.BackgroundJob;
import com.prelude.jobs.persistence.BackgroundJobMapper;
import com.prelude.jobs.persistence.JobAttempt;
import com.prelude.jobs.persistence.JobAttemptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final int MAX_FAILURE_SUMMARY_LENGTH = 1024;
    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(bearer\\s+)[^\\s,;]+");
    private static final Pattern NAMED_SECRET = Pattern.compile(
        "(?i)((?:api[-_ ]?key|authorization|token|secret)\\s*[:=]\\s*)[^\\s,;]+"
    );
    private static final Pattern OPENAI_STYLE_SECRET = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern URL_CREDENTIALS = Pattern.compile("(?i)(https?://)[^\\s/@]+@");
    private static final Pattern URL_QUERY = Pattern.compile("(?i)(https?://[^\\s?#]+)\\?[^\\s]+");

    private final BackgroundJobMapper jobMapper;
    private final JobAttemptMapper attemptMapper;
    private final ApplicationEventPublisher eventPublisher;

    public BackgroundJobService(
        BackgroundJobMapper jobMapper,
        JobAttemptMapper attemptMapper,
        ApplicationEventPublisher eventPublisher
    ) {
        this.jobMapper = jobMapper;
        this.attemptMapper = attemptMapper;
        this.eventPublisher = eventPublisher;
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
        BackgroundJob job = requireJob(jobId);
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.SUCCEEDED)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
        if (updated == 1) {
            closeAttempt(jobId, job.getAttemptCount(), JobAttempt.SUCCEEDED, null);
            eventPublisher.publishEvent(new BackgroundJobSucceeded(
                job.getJobId(), job.getType(), job.getAccountId(), job.getSubjectId()));
        } else {
            log.info("Ignoring duplicate completion for job {} (state={})", jobId, requireJob(jobId).getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FailureOutcome fail(String jobId, Throwable failure) {
        String sanitizedFailure = sanitizeFailure(failure);
        BackgroundJob job = requireJob(jobId);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
                .set(BackgroundJob::getStatus, BackgroundJob.PENDING)
                .set(BackgroundJob::getLastError, sanitizedFailure)
                .set(BackgroundJob::getClaimedAt, null)
                .eq(BackgroundJob::getJobId, jobId)
                .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
            if (updated != 1) {
                return FailureOutcome.NOT_RUNNING;
            }
            closeAttempt(jobId, job.getAttemptCount(), JobAttempt.FAILED, sanitizedFailure);
            eventPublisher.publishEvent(new BackgroundJobRequested(jobId));
            return FailureOutcome.RETRY_SCHEDULED;
        }
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<BackgroundJob>()
            .set(BackgroundJob::getStatus, BackgroundJob.FAILED)
            .set(BackgroundJob::getLastError, sanitizedFailure)
            .set(BackgroundJob::getFinishedAt, LocalDateTime.now())
            .eq(BackgroundJob::getJobId, jobId)
            .eq(BackgroundJob::getStatus, BackgroundJob.RUNNING));
        if (updated != 1) {
            return FailureOutcome.NOT_RUNNING;
        }
        closeAttempt(jobId, job.getAttemptCount(), JobAttempt.FAILED, sanitizedFailure);
        eventPublisher.publishEvent(new BackgroundJobFailed(
            job.getJobId(), job.getType(), job.getAccountId(), job.getSubjectId(), sanitizedFailure));
        return FailureOutcome.TERMINAL_FAILED;
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

    private void closeAttempt(String jobId, int attemptNumber, String status, String failureSummary) {
        attemptMapper.update(null, new LambdaUpdateWrapper<JobAttempt>()
            .set(JobAttempt::getStatus, status)
            .set(JobAttempt::getFinishedAt, LocalDateTime.now())
            .set(JobAttempt::getFailureSummary, failureSummary)
            .eq(JobAttempt::getJobId, jobId)
            .eq(JobAttempt::getAttemptNumber, attemptNumber)
            .eq(JobAttempt::getStatus, JobAttempt.RUNNING));
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

    private String sanitizeFailure(Throwable failure) {
        String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
            ? failure == null ? "Unknown failure" : failure.getClass().getSimpleName()
            : failure.getMessage();
        String safe = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\p{Cntrl}", " ");
        safe = URL_CREDENTIALS.matcher(safe).replaceAll("$1[REDACTED]@");
        safe = URL_QUERY.matcher(safe).replaceAll("$1?[REDACTED]");
        safe = BEARER_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        safe = NAMED_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        safe = OPENAI_STYLE_SECRET.matcher(safe).replaceAll("[REDACTED]");
        return safe.length() <= MAX_FAILURE_SUMMARY_LENGTH
            ? safe
            : safe.substring(0, MAX_FAILURE_SUMMARY_LENGTH);
    }
}
