package com.interview.platform.job.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.shared.api.BusinessException;
import com.interview.bootstrap.RabbitMqConfig;
import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.AsyncJob;
import com.interview.platform.job.JobRequest;
import com.interview.platform.job.JobSchedulerPort;
import com.interview.platform.job.JobStatuses;
import com.interview.platform.job.JobTicket;
import com.interview.platform.job.JobTypes;
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
        } else if (!JobStatuses.FAILED.equals(job.getStatus())) {
            return new JobTicket(job.getJobId(), job.getStatus());
        } else {
            resetForRetry(job, request.payloadJson());
        }

        try {
            rabbitTemplate.convertAndSend(
                RabbitMqConfig.REPORT_EXCHANGE,
                RabbitMqConfig.REPORT_ROUTING_KEY,
                new ReportJobMessage(request.subjectId(), request.userId(), job.getJobId())
            );
            job.setDispatchedAt(LocalDateTime.now());
            asyncJobMapper.updateById(job);
            log.info("Published {} job {} for subject {}", request.type(), job.getJobId(), request.subjectId());
            return new JobTicket(job.getJobId(), JobStatuses.PENDING);
        } catch (Exception exception) {
            job.setStatus(JobStatuses.FAILED);
            job.setLastError(errorMessage(exception));
            job.setFinishedAt(LocalDateTime.now());
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

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
