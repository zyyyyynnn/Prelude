package com.interview.insight.application;

import com.interview.platform.job.ReportJobMessage;
import com.interview.platform.job.infrastructure.JobExecutionStore;
import com.interview.platform.job.infrastructure.JobExecutionStore.ClaimResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReportGenerateHandler {

    private final GenerateInterviewReport generateInterviewReport;
    private final JobExecutionStore jobExecutionStore;
    private final int maxAttempts;

    public ReportGenerateHandler(
        GenerateInterviewReport generateInterviewReport,
        JobExecutionStore jobExecutionStore,
        @Value("${prelude.jobs.report.max-attempts:3}") int maxAttempts
    ) {
        this.generateInterviewReport = generateInterviewReport;
        this.jobExecutionStore = jobExecutionStore;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public void handle(ReportJobMessage message) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ClaimResult claim = jobExecutionStore.claimAttempt(message, maxAttempts);
            if (claim == ClaimResult.EXHAUSTED) {
                RuntimeException exhausted = new RuntimeException("报告任务已达到最大重试次数");
                jobExecutionStore.markFailed(message.jobId(), exhausted);
                generateInterviewReport.handleTerminalFailure(message.sessionId(), exhausted);
                return;
            }
            if (claim != ClaimResult.STARTED) {
                log.info("Skipping duplicate or terminal report job {}", message.jobId());
                return;
            }
            try {
                generateInterviewReport.execute(message.sessionId(), message.userId());
                jobExecutionStore.markSucceeded(message.jobId());
                return;
            } catch (RuntimeException error) {
                if (attempt < maxAttempts) {
                    jobExecutionStore.markRetry(message.jobId(), error);
                    log.warn("Report job {} failed on attempt {}/{}, retrying", message.jobId(), attempt, maxAttempts);
                    continue;
                }
                jobExecutionStore.markFailed(message.jobId(), error);
                generateInterviewReport.handleTerminalFailure(message.sessionId(), error);
            }
        }
    }
}
