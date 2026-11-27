package com.prelude.artifact.infrastructure;

import com.prelude.artifact.application.ReportGenerateHandler;
import com.prelude.jobs.integration.JobWorker;
import com.prelude.jobs.integration.BackgroundJobMessages.BackgroundJobView;
import com.prelude.jobs.integration.BackgroundJobRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Thin Rabbit consumer for report jobs. The broker message carries only the
 * jobId; the authoritative job (subject, account) is re-read through the
 * jobs integration boundary, and execution goes through the same boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "prelude.jobs.report.consumer-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ReportJobWorker {

    private final ReportGenerateHandler reportGenerateHandler;
    private final JobWorker jobWorker;

    @RabbitListener(queues = "${prelude.jobs.report.queue:prelude.job.report.queue}")
    public void handleReportJob(BackgroundJobRequested event) {
        BackgroundJobView job = jobWorker.dispatchedJob(event.jobId());
        reportGenerateHandler.handle(job.jobId(), job.subjectId(), job.accountId());
    }
}
