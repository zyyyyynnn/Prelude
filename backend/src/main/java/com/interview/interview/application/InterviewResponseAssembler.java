package com.interview.interview.application;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps domain entities to application query views.
 * Contains no business logic, no DB access, no side effects.
 */
@Component
public class InterviewResponseAssembler {

    public InterviewSessionSummary toSessionItem(InterviewSession session, String currentStage) {
        return new InterviewSessionSummary(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus(),
            session.getCreatedAt(),
            currentStage,
            session.getLlmProvider(),
            session.getLlmModel(),
            session.getSummaryReport()
        );
    }

    public InterviewSessionDetails toMessagesResponse(
        InterviewSession session,
        List<InterviewStage> stages,
        List<InterviewMessage> messages
    ) {
        String currentStage = stages.isEmpty()
            ? InterviewStageManager.STAGE_WARMUP
            : stages.get(stages.size() - 1).getStageName();

        return new InterviewSessionDetails(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus(),
            currentStage,
            session.getSummaryReport(),
            stages.stream().map(this::toStageItem).toList(),
            messages.stream().map(this::toMessageItem).toList(),
            session.getResumeId(),
            session.getPositionId(),
            session.getJdText()
        );
    }

    private InterviewStageView toStageItem(InterviewStage stage) {
        return new InterviewStageView(stage.getStageName(), stage.getStartedAt(), stage.getEndedAt());
    }

    private InterviewMessageView toMessageItem(InterviewMessage message) {
        return new InterviewMessageView(
            message.getId(),
            message.getRole(),
            message.getContent(),
            message.getSeqNum(),
            message.getCreatedAt(),
            message.getScore(),
            message.getHint()
        );
    }
}
