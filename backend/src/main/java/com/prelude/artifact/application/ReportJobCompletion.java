package com.prelude.artifact.application;

import com.prelude.artifact.application.GenerateInterviewReport.GenerationResult;
import com.prelude.artifact.application.GenerateInterviewReport.Outcome;
import com.prelude.artifact.application.port.InsightRepository;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.jobs.integration.BackgroundJobOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically commits report-domain state with the fenced job success. The job
 * CAS is attempted first, so a worker that has lost its attempt fence cannot
 * mutate report state. Any later critical failure rolls the whole transaction
 * back, including the job transition and its event publication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportJobCompletion {

    private final BackgroundJobOperations backgroundJobOperations;
    private final InterviewReportPort interviewReportPort;
    private final InsightRepository insightRepository;

    @Transactional(rollbackFor = Exception.class)
    public boolean complete(
        String jobId,
        int attemptNumber,
        Long sessionId,
        GenerationResult result
    ) {
        if (!backgroundJobOperations.complete(jobId, attemptNumber)) {
            log.info("Ignoring stale report completion for job {} attempt {}", jobId, attemptNumber);
            return false;
        }
        if (result.outcome() == Outcome.SKIPPED) {
            return true;
        }

        interviewReportPort.closeCurrentStage(sessionId);
        if (!interviewReportPort.completeReport(sessionId, result.reportJson())) {
            throw new IllegalStateException(
                "Report session lost generating state before finalization: " + sessionId);
        }
        persistInsights(sessionId, result);
        return true;
    }

    private void persistInsights(Long sessionId, GenerationResult result) {
        if (result.scoreHistory() != null) {
            insightRepository.replaceScore(result.scoreHistory());
        }
        insightRepository.replaceWeaknesses(sessionId, result.weaknesses());
    }
}
