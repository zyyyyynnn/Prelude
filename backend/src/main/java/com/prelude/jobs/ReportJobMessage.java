package com.prelude.jobs;

public record ReportJobMessage(Long sessionId, Long userId, String jobId) {
}
