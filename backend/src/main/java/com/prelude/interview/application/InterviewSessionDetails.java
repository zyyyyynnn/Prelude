package com.prelude.interview.application;

import java.util.List;

public record InterviewSessionDetails(
    Long sessionId,
    String targetPosition,
    String status,
    String currentStage,
    String model,
    String reasoningLevel,
    String summaryReport,
    List<InterviewStageView> stages,
    List<InterviewMessageView> messages,
    Long resumeId,
    Long positionId,
    String jdText,
    List<InterviewAttachmentView> attachments
) {
}
