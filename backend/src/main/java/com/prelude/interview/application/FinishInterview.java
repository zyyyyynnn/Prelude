package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStageDefinition;
import com.prelude.interview.integration.InterviewSessionStatus;
import com.prelude.interview.integration.InterviewStageCatalog;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.jobs.integration.JobDispatcher;
import com.prelude.jobs.integration.BackgroundJobMessages.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobMessages.BackgroundJobRequest;
import com.prelude.jobs.integration.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinishInterview {

    private static final InterviewStageCatalog.StageDescription CLOSING_STAGE =
        InterviewStageCatalog.require(InterviewStageDefinition.CLOSING.key());
    private static final String JOB_TYPE_REPORT = JobTypes.REPORT_GENERATE;

    private final InterviewSessionAccess sessionAccess;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final JobDispatcher jobDispatcher;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public FinishInterviewResult execute(Long sessionId) {
        long accountId = sessionAccess.currentAccountId();
        InterviewSession session = sessionAccess.requireOwned(sessionId, accountId);
        FinishInterviewResult terminal = terminalResult(session);
        if (terminal != null) {
            return terminal;
        }

        InterviewSessionStatus lockedStatus = interviewSessionRepository.lockAppendOrder(sessionId);
        if (lockedStatus == null) {
            throw BusinessException.badRequest("面试会话不存在");
        }
        switch (lockedStatus) {
            case FINISHED -> {
                InterviewSession finished = interviewSessionRepository.selectByIdForUpdate(sessionId);
                if (finished == null || accountId != finished.getAccountId()) {
                    throw BusinessException.badRequest("面试会话不存在或无权访问");
                }
                return new FinishInterviewResult(
                    session.getId(), finished.getSummaryReport(), InterviewSessionStatus.FINISHED.wire(), null);
            }
            case GENERATING -> {
                return new FinishInterviewResult(
                    session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
            }
            case ONGOING -> {
            }
        }
        if (!CLOSING_STAGE.wireName().equals(interviewStageManager.currentStageNameForUpdate(sessionId))) {
            throw BusinessException.badRequest("仅在收尾阶段才能生成报告");
        }
        if (interviewMessageRepository.countPendingAssessments(sessionId) > 0) {
            throw BusinessException.conflict("assessment_pending", "收尾回答仍在评估中，请稍后再试");
        }
        interviewStageManager.closeCurrentStage(sessionId);

        if (interviewSessionRepository.markGeneratingIfOngoing(sessionId, accountId) != 1) {
            return new FinishInterviewResult(
                session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
        }

        String operationId = UUID.randomUUID().toString();
        BackgroundJobRef job = jobDispatcher.request(new BackgroundJobRequest(
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

    private FinishInterviewResult terminalResult(InterviewSession session) {
        return switch (session.getStatus()) {
            case ONGOING -> null;
            case GENERATING -> new FinishInterviewResult(
                session.getId(), null, InterviewSessionStatus.GENERATING.wire(), null);
            case FINISHED -> new FinishInterviewResult(
                session.getId(), session.getSummaryReport(), InterviewSessionStatus.FINISHED.wire(), null);
        };
    }
}
