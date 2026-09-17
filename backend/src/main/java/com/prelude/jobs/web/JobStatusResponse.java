package com.prelude.jobs.web;

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
