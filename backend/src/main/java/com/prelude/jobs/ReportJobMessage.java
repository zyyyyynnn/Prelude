package com.prelude.jobs;

public record ReportJobMessage(Long sessionId, Long accountId, String jobId) {
}
