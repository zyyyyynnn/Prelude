package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_GENERATING = "generating";
    private static final String STATUS_FINISHED = "finished";
    private static final String STAGE_CLOSING = "closing";

    private static final String JOB_TYPE_REPORT = "report.generate";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final BackgroundJobOperations backgroundJobOperations;
    private final InterviewMessageService interviewMessageService;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewResult execute(Long sessionId) {
        long accountId = sessionAccess.currentAccountId();
        InterviewSession session = sessionAccess.requireOwned(sessionId, accountId);
        String status = session.getStatus();

        if (STATUS_GENERATING.equals(status)) {
            return new FinishInterviewResult(session.getId(), null, STATUS_GENERATING, null);
        }
        if (STATUS_FINISHED.equals(status)) {
            return new FinishInterviewResult(session.getId(), session.getSummaryReport(), STATUS_FINISHED, null);
        }
        if (!STATUS_ONGOING.equals(status)) {
            throw BusinessException.badRequest("面试会话状态异常");
        }
        if (!STAGE_CLOSING.equals(interviewStageManager.currentStageName(sessionId))) {
            throw BusinessException.badRequest("仅在收尾阶段才能生成报告");
        }

        session.setStatus(STATUS_GENERATING);
        interviewSessionRepository.update(session);

        BackgroundJobRef job = backgroundJobOperations.request(new BackgroundJobRequest(
            JOB_TYPE_REPORT,
            accountId,
            sessionId,
            JOB_TYPE_REPORT + ":session:" + sessionId,
            "{}"
        ));
        log.info("Requested report generation job {} for session {}", job.jobId(), sessionId);

        interviewMessageService.invalidateSessionLock(sessionId);
        return new FinishInterviewResult(session.getId(), null, STATUS_GENERATING, job.jobId());
    }
}
