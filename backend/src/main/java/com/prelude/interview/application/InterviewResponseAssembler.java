package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.llm.api.ModelExecutionPort.FrozenModelConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewResponseAssembler {

    public InterviewSessionSummary toSessionItem(InterviewSession session, String currentStage) {
        return new InterviewSessionSummary(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus().wire(),
            session.getCreatedAt(),
            currentStage,
            session.getSummaryReport(),
            session.getPinnedAt() != null
        );
    }

    public InterviewSessionDetails toMessagesResponse(
        InterviewSession session,
        List<InterviewStage> stages,
        List<InterviewMessage> messages,
        FrozenModelConfiguration modelConfiguration,
        List<InterviewAttachmentView> attachments
    ) {
        if (stages.isEmpty()) {
            throw new IllegalStateException("Interview session has no stage: " + session.getId());
        }
        String currentStage = stages.getLast().getStageName();

        return new InterviewSessionDetails(
            session.getId(),
            session.getTargetPosition(),
            session.getStatus().wire(),
            currentStage,
            modelConfiguration.model(),
            modelConfiguration.reasoningLevel(),
            session.getSummaryReport(),
            stages.stream().map(this::toStageItem).toList(),
            messages.stream().map(this::toMessageItem).toList(),
            session.getResumeId(),
            session.getPositionId(),
            session.getJdText(),
            attachments
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
            message.getHint(),
            message.getAssessmentStatus(),
            message.getAssessmentError()
        );
    }
}
