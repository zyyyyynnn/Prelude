package com.prelude.artifact.infrastructure;

import com.prelude.artifact.application.ReportGenerateHandler;
import com.prelude.jobs.ReportJobChannel;
import com.prelude.jobs.ReportJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportJobWorker {

    private final ReportGenerateHandler reportGenerateHandler;

    @RabbitListener(queues = ReportJobChannel.QUEUE)
    public void handleReportJob(ReportJobMessage job) {
        log.info("Received RabbitMQ report job: {}", job);
        reportGenerateHandler.handle(job);
    }
}
