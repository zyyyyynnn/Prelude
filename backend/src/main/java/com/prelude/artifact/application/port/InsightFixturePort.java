package com.prelude.artifact.application.port;

import com.prelude.artifact.domain.UserWeakness;

import java.util.List;

public interface InsightFixturePort {

    boolean isEnabled();

    String resolveReport(String targetPosition);

    List<UserWeakness> buildWeaknesses(Long userId, Long sessionId);
}
