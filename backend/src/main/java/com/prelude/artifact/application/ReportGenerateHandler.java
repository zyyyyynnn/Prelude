package com.prelude.artifact.application;

import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.ClaimOutcome;
import com.prelude.jobs.integration.BackgroundJobOperations.FailureOutcome;
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
            GenerateInterviewReport.Outcome outcome = generateInterviewReport.execute(sessionId, accountId);
            switch (outcome) {
                case GENERATED, SKIPPED -> backgroundJobOperations.complete(jobId);
            }
        } catch (RuntimeException error) {
            FailureOutcome failure = backgroundJobOperations.fail(jobId, error);
            if (failure == FailureOutcome.TERMINAL_FAILED) {
                generateInterviewReport.handleTerminalFailure(sessionId, error);
            }
        }
    }
}
