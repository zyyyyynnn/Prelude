package com.interview.interview.application;

import java.util.List;

public record InterviewSessionDetails(
    Long sessionId,
    String targetPosition,
    String status,
    String currentStage,
    String summaryReport,
    List<InterviewStageView> stages,
    List<InterviewMessageView> messages,
    Long resumeId,
    Long positionId,
    String jdText
) {
}
