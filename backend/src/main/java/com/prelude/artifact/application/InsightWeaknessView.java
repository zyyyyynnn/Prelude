package com.prelude.artifact.application;

import java.util.List;

public record InsightWeaknessView(
    String category,
    int count,
    List<String> descriptions
) {
}
