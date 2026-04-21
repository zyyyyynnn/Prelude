package com.interview.dto;

import java.util.List;

public record InterviewMessagesResponse(
    Long sessionId,
    String targetPosition,
    String status,
    String currentStage,
    String summaryReport,
    List<InterviewStageItemResponse> stages,
    List<InterviewMessageItemResponse> messages
) {
}
