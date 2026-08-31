package com.prelude.interview.api;

import java.util.List;

public record InterviewMessagesResponse(
    Long sessionId,
    String targetPosition,
    String status,
    String currentStage,
    String summaryReport,
    List<InterviewStageItemResponse> stages,
    List<InterviewMessageItemResponse> messages,
    Long resumeId,
    Long positionId,
    String jdText,
    List<InterviewAttachmentItemResponse> attachments
) {
}
