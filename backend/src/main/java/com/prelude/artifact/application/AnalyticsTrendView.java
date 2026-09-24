package com.prelude.artifact.application;

import java.time.LocalDateTime;

public record AnalyticsTrendView(
    Long sessionId,
    LocalDateTime createdAt,
    Integer technical,
    Integer expression,
    Integer logic
) {
}
