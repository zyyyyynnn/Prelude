package com.interview.insight.application.port;

import com.interview.insight.domain.UserWeakness;

import java.util.List;

public interface InsightFixturePort {

    boolean isEnabled();

    String resolveReport(String targetPosition);

    List<UserWeakness> buildWeaknesses(Long userId, Long sessionId);
}
