package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.platform.job.JobRequest;
import com.interview.platform.job.JobSchedulerPort;
import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTicket;
import com.interview.platform.job.JobTypes;
import com.interview.platform.job.ReportJobChannel;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.persistence.AsyncJob;
import com.interview.platform.job.infrastructure.persistence.AsyncJobMapper;
import com.interview.shared.api.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitJobScheduler implements JobSchedulerPort {

    private final AsyncJobMapper asyncJobMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public JobTicket enqueue(JobRequest request) {
        if (!JobTypes.REPORT_GENERATE.equals(request.type())) {
            throw BusinessException.badRequest("不支持的任务类型");
        }

        AsyncJob job = findByIdempotencyKey(request.idempotencyKey());
        if (job == null) {
            job = createPending(request);
        } else if (JobStatuses.FAILED.equals(job.getStatus())) {
            resetForRetry(job, request.payloadJson());
        } else if (JobStatuses.PENDING.equals(job.getStatus()) && job.getDispatchedAt() == null) {
            // DB row exists but MQ publish never succeeded; fall through and retry dispatch.
        } else {
            return new JobTicket(job.getJobId(), job.getStatus());
        }

        try {
            rabbitTemplate.convertAndSend(
                ReportJobChannel.EXCHANGE,
                ReportJobChannel.ROUTING_KEY,
                new ReportJobMessage(request.subjectId(), request.userId(), job.getJobId())
            );
            job.setDispatchedAt(LocalDateTime.now());
            job.setLastError(null);
            asyncJobMapper.updateById(job);
            log.info("Published {} job {} for subject {}", request.type(), job.getJobId(), request.subjectId());
            return new JobTicket(job.getJobId(), JobStatuses.PENDING);
        } catch (Exception exception) {
            log.error("Failed to publish {} job {} for subject {} (type={})",
                request.type(), job.getJobId(), request.subjectId(), exception.getClass().getSimpleName());
            // Keep PENDING so recovery polling and client retry can redispatch; do not finish the job.
            job.setLastError(JobFailureMessage.sanitize(exception));
            asyncJobMapper.updateById(job);
            throw BusinessException.badRequest("报告生成任务发布失败");
        }
    }

    private AsyncJob createPending(JobRequest request) {
        AsyncJob job = new AsyncJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setType(request.type());
        job.setUserId(request.userId());
        job.setSubjectId(request.subjectId());
        job.setIdempotencyKey(request.idempotencyKey());
        job.setStatus(JobStatuses.PENDING);
        job.setAttempts(0);
        job.setPayloadJson(request.payloadJson());
        try {
            asyncJobMapper.insert(job);
            return job;
        } catch (DuplicateKeyException duplicate) {
            AsyncJob existing = findByIdempotencyKey(request.idempotencyKey());
            if (existing == null) {
                throw duplicate;
            }
            return existing;
        }
    }

    private void resetForRetry(AsyncJob job, String payloadJson) {
        job.setStatus(JobStatuses.PENDING);
        job.setAttempts(0);
        job.setPayloadJson(payloadJson);
        job.setLastError(null);
        job.setDispatchedAt(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        asyncJobMapper.updateById(job);
    }

    private AsyncJob findByIdempotencyKey(String idempotencyKey) {
        return asyncJobMapper.selectOne(new LambdaQueryWrapper<AsyncJob>()
            .eq(AsyncJob::getIdempotencyKey, idempotencyKey)
            .last("LIMIT 1"));
    }

}
