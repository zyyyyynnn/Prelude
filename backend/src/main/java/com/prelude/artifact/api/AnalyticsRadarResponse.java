package com.prelude.artifact.api;

public record AnalyticsRadarResponse(
    double technical,
    double expression,
    double logic,
    long sessionCount
) {
}
