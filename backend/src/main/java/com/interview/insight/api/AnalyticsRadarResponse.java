package com.interview.insight.api;

public record AnalyticsRadarResponse(
    double technical,
    double expression,
    double logic,
    long sessionCount
) {
}
