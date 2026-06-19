package com.interview.service.impl;

import com.interview.dto.InterviewMessageItemResponse;
import com.interview.dto.InterviewMessagesResponse;
import com.interview.dto.InterviewSessionItemResponse;
import com.interview.dto.InterviewStageItemResponse;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pure response assembly — maps domain entities to DTOs.
 * Contains no business logic, no DB access, no side effects.
 */
@Component
public class InterviewResponseAssembler {

    public InterviewSessionItemResponse toSessionItem(InterviewSession session, String currentStage) {
        return new InterviewSessionItemResponse(
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

    public InterviewMessagesResponse toMessagesResponse(
        InterviewSession session,
        List<InterviewStage> stages,
        List<InterviewMessage> messages
    ) {
        String currentStage = stages.isEmpty()
            ? InterviewStageManager.STAGE_WARMUP
            : stages.get(stages.size() - 1).getStageName();

        return new InterviewMessagesResponse(
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

    private InterviewStageItemResponse toStageItem(InterviewStage stage) {
        return new InterviewStageItemResponse(stage.getStageName(), stage.getStartedAt(), stage.getEndedAt());
    }

    private InterviewMessageItemResponse toMessageItem(InterviewMessage message) {
        return new InterviewMessageItemResponse(
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
