package com.interview.dto;

public record AnalyticsRadarResponse(
    double technical,
    double expression,
    double logic,
    long sessionCount
) {
}
