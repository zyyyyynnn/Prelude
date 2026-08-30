package com.prelude.artifact.application.port;

import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.AccountWeakness;

import java.util.List;

public interface InsightRepository {

    List<ScoreHistory> recentScores(Long accountId, int limit);

    List<AccountWeakness> listWeaknessesByAccount(Long accountId);

    List<AccountWeakness> listWeaknessesBySession(Long sessionId);

    void replaceScore(ScoreHistory score);

    void replaceWeaknesses(Long sessionId, List<AccountWeakness> weaknesses);
}
