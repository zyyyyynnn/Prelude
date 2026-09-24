package com.prelude.artifact.application;

import java.util.List;

public record AnalyticsWeaknessView(
    String category,
    int count,
    List<String> descriptions
) {
}
