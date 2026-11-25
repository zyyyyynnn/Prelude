package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.api.port.InterviewSessionStatus;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.repository.InterviewMessageRepository;
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

/** Finishes the session and schedules its report in one transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinishInterview {

    private static final String STAGE_CLOSING = "closing";

    private static final String JOB_TYPE_REPORT = JobTypes.REPORT_GENERATE;

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
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

        String lockedStatus = interviewSessionRepository.lockAppendOrder(sessionId);
        if (lockedStatus == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        if (InterviewSessionStatus.GENERATING.matches(lockedStatus)) {
            return new FinishInterviewResult(session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
        }
        if (InterviewSessionStatus.FINISHED.matches(lockedStatus)) {
            InterviewSession finished = interviewSessionRepository.selectByIdForUpdate(sessionId);
            if (finished == null || accountId != finished.getAccountId()) {
                throw BusinessException.badRequest("面试会话不存在或无权访问");
            }
            return new FinishInterviewResult(
                session.getId(), finished.getSummaryReport(), InterviewSessionStatus.FINISHED.wire(), null);
        }
        if (!InterviewSessionStatus.ONGOING.matches(lockedStatus)) {
            throw BusinessException.badRequest("面试会话状态异常");
        }
        if (!STAGE_CLOSING.equals(interviewStageManager.currentStageName(sessionId))) {
            throw BusinessException.badRequest("仅在收尾阶段才能生成报告");
        }
        if (interviewMessageRepository.countPendingAssessments(sessionId) > 0) {
            throw BusinessException.conflict("assessment_pending", "收尾回答仍在评估中，请稍后再试");
        }
        interviewStageManager.closeCurrentStage(sessionId);

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
