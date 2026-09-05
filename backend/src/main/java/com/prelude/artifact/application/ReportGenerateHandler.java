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
    private final ReportJobCompletion reportJobCompletion;
    private final BackgroundJobOperations backgroundJobOperations;

    public void handle(String jobId, Long sessionId, Long accountId) {
        ClaimOutcome claim = backgroundJobOperations.claim(jobId);
        if (!claim.claimed()) {
            log.info("Skipping duplicate or terminal report job {} (state: {})", jobId, claim.status());
            return;
        }
        int attemptNumber = claim.attemptNumber();
        try (BackgroundJobOperations.ExecutionLease ignored =
                 backgroundJobOperations.keepLeaseAlive(jobId, attemptNumber)) {
            try {
                GenerateInterviewReport.GenerationResult result =
                    generateInterviewReport.execute(sessionId, accountId);
                if (!reportJobCompletion.complete(jobId, attemptNumber, sessionId, result)) {
                    log.info("Skipping stale report job completion {} attempt {}", jobId, attemptNumber);
                }
            } catch (RuntimeException error) {
                backgroundJobOperations.fail(jobId, attemptNumber, error);
            }
        }
    }
}
