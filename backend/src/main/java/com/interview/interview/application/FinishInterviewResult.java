package com.interview.interview.application;

public record FinishInterviewResult(
    Long sessionId,
    String summaryReport,
    String status,
    String jobId
) {
}
