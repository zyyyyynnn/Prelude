package com.prelude.artifact;

import com.prelude.artifact.application.port.InsightFixturePort;
import com.prelude.artifact.domain.UserWeakness;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("artifactFixtureAdapter")
class FixtureAdapter implements InsightFixturePort {
    @Override public boolean isEnabled() { return false; }
    @Override public String resolveReport(String targetPosition) { throw new UnsupportedOperationException(); }
    @Override public List<UserWeakness> buildWeaknesses(Long userId, Long sessionId) { return List.of(); }
}
