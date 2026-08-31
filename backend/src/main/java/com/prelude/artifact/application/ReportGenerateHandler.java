package com.prelude.artifact.application;

import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin job body for report generation. Claim/attempt/retry/duplicate/job
 * status belong to the jobs executor; this handler only runs the domain
 * generation once a claim succeeds and reports the outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerateHandler {

    private final GenerateInterviewReport generateInterviewReport;
    private final BackgroundJobOperations backgroundJobOperations;

    public void handle(String jobId, Long sessionId, Long accountId) {
        ClaimOutcome claim = backgroundJobOperations.claim(jobId);
        if (!claim.claimed()) {
            log.info("Skipping duplicate or terminal report job {} (state: {})", jobId, claim.status());
            return;
        }
        try {
            generateInterviewReport.execute(sessionId, accountId);
            backgroundJobOperations.complete(jobId);
        } catch (RuntimeException error) {
            generateInterviewReport.handleTerminalFailure(sessionId, error);
            backgroundJobOperations.fail(jobId, sanitize(error));
        }
    }

    /**
     * Failure summaries are persisted; they must never carry secrets or
     * provider payloads. Class + message only.
     */
    private String sanitize(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName()
            : error.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
