package com.interview.insight.application;

import java.util.List;

public record InsightWeaknessView(
    String category,
    int count,
    List<String> descriptions
) {
}
