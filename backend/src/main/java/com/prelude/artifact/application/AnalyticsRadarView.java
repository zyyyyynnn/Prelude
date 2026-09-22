package com.prelude.artifact.application;

public record AnalyticsRadarView(
    double technical,
    double expression,
    double logic,
    int sessionCount
) {
}
