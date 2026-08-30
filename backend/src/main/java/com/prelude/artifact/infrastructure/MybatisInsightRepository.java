package com.prelude.artifact.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.artifact.application.port.InsightRepository;
import com.prelude.artifact.infrastructure.persistence.ScoreHistoryMapper;
import com.prelude.artifact.infrastructure.persistence.AccountWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisInsightRepository implements InsightRepository {

    private final ScoreHistoryMapper scoreHistoryMapper;
    private final AccountWeaknessMapper accountWeaknessMapper;

    @Override
    public List<ScoreHistory> recentScores(Long accountId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return scoreHistoryMapper.selectList(new LambdaQueryWrapper<ScoreHistory>()
            .eq(ScoreHistory::getAccountId, accountId)
            .orderByDesc(ScoreHistory::getCreatedAt)
            .last("LIMIT " + safeLimit));
    }

    @Override
    public List<AccountWeakness> listWeaknessesByAccount(Long accountId) {
        return accountWeaknessMapper.selectList(new LambdaQueryWrapper<AccountWeakness>()
            .eq(AccountWeakness::getAccountId, accountId)
            .orderByDesc(AccountWeakness::getCreatedAt)
            .orderByAsc(AccountWeakness::getId));
    }

    @Override
    public List<AccountWeakness> listWeaknessesBySession(Long sessionId) {
        return accountWeaknessMapper.selectList(new LambdaQueryWrapper<AccountWeakness>()
            .eq(AccountWeakness::getSessionId, sessionId)
            .orderByAsc(AccountWeakness::getCreatedAt)
            .orderByAsc(AccountWeakness::getId));
    }

    @Override
    public void replaceScore(ScoreHistory score) {
        scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
            .eq(ScoreHistory::getSessionId, score.getSessionId()));
        scoreHistoryMapper.insert(score);
    }

    @Override
    public void replaceWeaknesses(Long sessionId, List<AccountWeakness> weaknesses) {
        accountWeaknessMapper.delete(new LambdaQueryWrapper<AccountWeakness>()
            .eq(AccountWeakness::getSessionId, sessionId));
        for (AccountWeakness weakness : weaknesses) {
            accountWeaknessMapper.insert(weakness);
        }
    }
}
