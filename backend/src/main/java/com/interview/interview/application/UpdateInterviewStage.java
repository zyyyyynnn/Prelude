package com.interview.interview.application;

import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import com.interview.interview.application.port.InterviewFixturePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInterviewStage {

    private static final String ROLE_ASSISTANT = "assistant";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewStageManager interviewStageManager;
    private final InterviewMessageService interviewMessageService;
    private final InterviewFixturePort devFixtureService;

    @Transactional(rollbackFor = Exception.class)
    public UpdateInterviewStageResult execute(Long sessionId, String stageName) {
        InterviewSession session = sessionAccess.requireOngoing(sessionId, sessionAccess.currentUserId());
        if (interviewStageManager.currentOrLatestStage(sessionId) == null) {
            interviewStageManager.ensureInitialStage(session);
        }
        InterviewStage stage = interviewStageManager.moveToStage(sessionId, stageName, true);
        if (devFixtureService != null && devFixtureService.isEnabled()) {
            interviewMessageService.insertMessage(
                sessionId,
                ROLE_ASSISTANT,
                devFixtureService.resolveScriptedReply(stage.getStageName(), 0)
            );
        }
        return new UpdateInterviewStageResult(stage.getStageName(), stage.getStartedAt());
    }
}
