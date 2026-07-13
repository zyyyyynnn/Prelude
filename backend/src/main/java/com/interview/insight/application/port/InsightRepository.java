package com.interview.insight.application.port;

import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;

import java.util.List;

public interface InsightRepository {

    List<ScoreHistory> recentScores(Long userId, int limit);

    List<UserWeakness> listWeaknessesByUser(Long userId);

    List<UserWeakness> listWeaknessesBySession(Long sessionId);

    void replaceScore(ScoreHistory score);

    void replaceWeaknesses(Long sessionId, List<UserWeakness> weaknesses);
}
