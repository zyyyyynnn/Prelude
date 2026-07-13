package com.interview.interview.api;

import com.interview.interview.application.FinishInterviewResult;
import com.interview.interview.application.InterviewMessageView;
import com.interview.interview.application.InterviewSessionDetails;
import com.interview.interview.application.InterviewSessionSummary;
import com.interview.interview.application.InterviewStageView;
import com.interview.interview.application.StartInterviewCommand;
import com.interview.interview.application.StartInterviewResult;
import com.interview.interview.application.UpdateInterviewStageResult;

final class InterviewApiMapper {

    private InterviewApiMapper() {
    }

    static StartInterviewCommand toCommand(InterviewStartRequest request) {
        return new StartInterviewCommand(
            request.getResumeId(),
            request.getPositionId(),
            request.getJdText(),
            request.getLlmModel()
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
            details.jdText()
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
