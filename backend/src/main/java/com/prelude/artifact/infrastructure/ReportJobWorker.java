package com.prelude.artifact.infrastructure;

import com.prelude.artifact.application.ReportGenerateHandler;
import com.prelude.jobs.infrastructure.RabbitMqConfig;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Thin Rabbit consumer for report jobs. The broker message carries only the
 * jobId; the authoritative job (subject, account) is re-read through the
 * jobs integration boundary, and execution goes through the same boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportJobWorker {

    private final ReportGenerateHandler reportGenerateHandler;
    private final BackgroundJobOperations backgroundJobOperations;

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void handleReportJob(String jobId) {
        BackgroundJobView job;
        try {
            job = backgroundJobOperations.dispatchedJob(jobId);
        } catch (RuntimeException exception) {
            log.warn("Received report job with no authoritative record: {}", jobId);
            return;
        }
        reportGenerateHandler.handle(job.jobId(), job.subjectId(), job.accountId());
    }
}
