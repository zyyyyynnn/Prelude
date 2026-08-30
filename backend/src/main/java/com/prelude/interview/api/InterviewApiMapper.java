package com.prelude.interview.api;

import com.prelude.interview.application.FinishInterviewResult;
import com.prelude.interview.application.InterviewMessageView;
import com.prelude.interview.application.InterviewSessionDetails;
import com.prelude.interview.application.InterviewSessionSummary;
import com.prelude.interview.application.InterviewStageView;
import com.prelude.interview.application.StartInterviewCommand;
import com.prelude.interview.application.StartInterviewResult;
import com.prelude.interview.application.UpdateInterviewStageResult;

final class InterviewApiMapper {

    private InterviewApiMapper() {
    }

    static StartInterviewCommand toCommand(InterviewStartRequest request) {
        return new StartInterviewCommand(
            request.getResumeId(),
            request.getPositionId(),
            request.getJdText(),
            request.getLlmModel(),
            request.getAttachmentIds()
        );
    }

    static InterviewStartResponse toResponse(StartInterviewResult result) {
        return new InterviewStartResponse(
            result.sessionId(), result.targetPosition(), result.currentStage()
        );
    }

    static InterviewStageUpdateResponse toResponse(UpdateInterviewStageResult result) {
        return new InterviewStageUpdateResponse(result.stageName(), result.startedAt());
    }

    static InterviewFinishResponse toResponse(FinishInterviewResult result) {
        return new InterviewFinishResponse(
            result.sessionId(), result.summaryReport(), result.status(), result.jobId()
        );
    }

    static InterviewSessionItemResponse toResponse(InterviewSessionSummary summary) {
        return new InterviewSessionItemResponse(
            summary.sessionId(),
            summary.targetPosition(),
            summary.status(),
            summary.createdAt(),
            summary.currentStage(),
            summary.llmProvider(),
            summary.llmModel(),
            summary.llmThinkingDepth(),
            summary.summaryReport()
        );
    }

    static InterviewMessagesResponse toResponse(InterviewSessionDetails details) {
        return new InterviewMessagesResponse(
            details.sessionId(),
            details.targetPosition(),
            details.status(),
            details.currentStage(),
            details.summaryReport(),
            details.stages().stream().map(InterviewApiMapper::toResponse).toList(),
            details.messages().stream().map(InterviewApiMapper::toResponse).toList(),
            details.resumeId(),
            details.positionId(),
            details.jdText(),
            details.llmThinkingDepth(),
            details.attachments().stream()
                .map(item -> new InterviewAttachmentItemResponse(
                    item.id(), item.fileName(), item.mediaType(), item.size(), item.image()))
                .toList()
        );
    }

    private static InterviewStageItemResponse toResponse(InterviewStageView stage) {
        return new InterviewStageItemResponse(stage.stageName(), stage.startedAt(), stage.endedAt());
    }

    private static InterviewMessageItemResponse toResponse(InterviewMessageView message) {
        return new InterviewMessageItemResponse(
            message.id(),
            message.role(),
            message.content(),
            message.seqNum(),
            message.createdAt(),
            message.score(),
            message.hint()
        );
    }
}
