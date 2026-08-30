package com.prelude.artifact.application;

public record InsightRadarView(
    double technical,
    double expression,
    double logic,
    int sessionCount
) {
}
