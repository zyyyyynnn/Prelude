package com.prelude.interview.api.port;

import java.time.LocalDateTime;

/** A stored interview stage as another module sees it. */
public record InterviewStageSnapshot(
    Long id,
    Long sessionId,
    String stageName,
    LocalDateTime startedAt,
    LocalDateTime endedAt
) {
}
