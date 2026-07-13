package com.interview.platform.job.api;

import java.time.LocalDateTime;

public record JobStatusResponse(
    String jobId,
    String type,
    Long subjectId,
    String status,
    int attempts,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
}
