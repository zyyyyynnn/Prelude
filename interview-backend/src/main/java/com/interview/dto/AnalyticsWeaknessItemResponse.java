package com.interview.dto;

import java.util.List;

public record AnalyticsWeaknessItemResponse(
    String category,
    long count,
    List<String> descriptions
) {
}
