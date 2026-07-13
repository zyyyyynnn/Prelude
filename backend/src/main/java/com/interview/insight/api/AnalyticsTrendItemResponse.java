package com.interview.insight.api;

import java.time.LocalDateTime;

public record AnalyticsTrendItemResponse(
    Long sessionId,
    LocalDateTime createdAt,
    Integer technical,
    Integer expression,
    Integer logic
) {
}
