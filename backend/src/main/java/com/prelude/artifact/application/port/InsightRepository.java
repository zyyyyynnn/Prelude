package com.prelude.artifact.application.port;

import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.UserWeakness;

import java.util.List;

public interface InsightRepository {

    List<ScoreHistory> recentScores(Long userId, int limit);

    List<UserWeakness> listWeaknessesByUser(Long userId);

    List<UserWeakness> listWeaknessesBySession(Long sessionId);

    void replaceScore(ScoreHistory score);

    void replaceWeaknesses(Long sessionId, List<UserWeakness> weaknesses);
}
