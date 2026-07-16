package com.interview.insight.infrastructure;

import com.interview.insight.application.ReportGenerateHandler;
import com.interview.platform.job.ReportJobChannel;
import com.interview.platform.job.ReportJobMessage;
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
