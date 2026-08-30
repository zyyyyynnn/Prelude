package com.prelude.interview.application;

public record FinishInterviewResult(
    Long sessionId,
    String summaryReport,
    String status,
    String jobId
) {
}
