package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInterviewStage {

    private final InterviewSessionAccess sessionAccess;
    private final InterviewStageManager interviewStageManager;

    @Transactional(rollbackFor = Exception.class)
    public UpdateInterviewStageResult execute(Long sessionId, String stageName) {
        InterviewSession session = sessionAccess.requireOngoing(sessionId, sessionAccess.currentAccountId());
        if (interviewStageManager.currentOrLatestStage(sessionId) == null) {
            interviewStageManager.ensureInitialStage(session);
        }
        InterviewStage stage = interviewStageManager.moveToStage(sessionId, stageName, true);
        return new UpdateInterviewStageResult(stage.getStageName(), stage.getStartedAt());
    }
}
