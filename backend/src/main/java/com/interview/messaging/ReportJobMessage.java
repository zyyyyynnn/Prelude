package com.interview.messaging;

public record ReportJobMessage(Long sessionId, Long userId, String jobId) {
}
