package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Finishes an interview and schedules the report. One transaction covers the
 * session state, the background_job row and the Spring Modulith publication;
 * the broker call happens only after commit. If the broker is down at that
 * point, the job stays PENDING with an incomplete publication and the
 * framework's recovery externalizes it — no status-compensation rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinishInterview {

    private static final String STAGE_CLOSING = "closing";

    private static final String JOB_TYPE_REPORT = JobTypes.REPORT_GENERATE;

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final BackgroundJobOperations backgroundJobOperations;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewResult execute(Long sessionId) {
        long accountId = sessionAccess.currentAccountId();
        InterviewSession session = sessionAccess.requireOwned(sessionId, accountId);
        String status = session.getStatus();

        if (InterviewSessionStatus.GENERATING.matches(status)) {
            return new FinishInterviewResult(session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
        }
        if (InterviewSessionStatus.FINISHED.matches(status)) {
            return new FinishInterviewResult(
                session.getId(), session.getSummaryReport(), InterviewSessionStatus.FINISHED.wire(), null);
        }
        if (!InterviewSessionStatus.ONGOING.matches(status)) {
            throw BusinessException.badRequest("面试会话状态异常");
        }
        if (!STAGE_CLOSING.equals(interviewStageManager.currentStageName(sessionId))) {
            throw BusinessException.badRequest("仅在收尾阶段才能生成报告");
        }

        if (interviewSessionRepository.markGeneratingIfOngoing(sessionId, accountId) != 1) {
            // Another request won the only valid ongoing -> generating transition.
            return new FinishInterviewResult(
                session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
        }

        String operationId = UUID.randomUUID().toString();
        BackgroundJobRef job = backgroundJobOperations.request(new BackgroundJobRequest(
            JOB_TYPE_REPORT,
            accountId,
            sessionId,
            JOB_TYPE_REPORT + ":session:" + sessionId + ":" + operationId,
            "{}"
        ));
        log.info("Requested report generation job {} for session {}", job.jobId(), sessionId);

        return new FinishInterviewResult(
            session.getId(), null, InterviewSessionStatus.GENERATING.wire(), job.jobId());
    }
}
