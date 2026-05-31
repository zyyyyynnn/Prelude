package com.interview.dto;

import java.time.LocalDateTime;

public record AnalyticsTrendItemResponse(
    Long sessionId,
    LocalDateTime createdAt,
    Integer technical,
    Integer expression,
    Integer logic
) {
}
