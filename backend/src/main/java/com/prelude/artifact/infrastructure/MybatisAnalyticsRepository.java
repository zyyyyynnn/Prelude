package com.prelude.artifact.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.artifact.application.port.AnalyticsRepository;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.artifact.infrastructure.persistence.ScoreHistoryMapper;
import com.prelude.artifact.infrastructure.persistence.ScoreHistoryEntity;
import com.prelude.artifact.infrastructure.persistence.AccountWeaknessMapper;
import com.prelude.artifact.infrastructure.persistence.AccountWeaknessEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisAnalyticsRepository implements AnalyticsRepository {

    private final ScoreHistoryMapper scoreHistoryMapper;
    private final AccountWeaknessMapper accountWeaknessMapper;

    @Override
    public List<ScoreHistory> recentScores(Long accountId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return scoreHistoryMapper.selectList(new LambdaQueryWrapper<ScoreHistoryEntity>()
            .eq(ScoreHistoryEntity::getAccountId, accountId)
            .orderByDesc(ScoreHistoryEntity::getCreatedAt)
            .last("LIMIT " + safeLimit))
            .stream()
            .map(ScoreHistoryEntity::toDomain)
            .toList();
    }

    @Override
    public List<AccountWeakness> listWeaknessesByAccount(Long accountId) {
        return accountWeaknessMapper.selectList(new LambdaQueryWrapper<AccountWeaknessEntity>()
            .eq(AccountWeaknessEntity::getAccountId, accountId)
            .orderByDesc(AccountWeaknessEntity::getCreatedAt)
            .orderByAsc(AccountWeaknessEntity::getId))
            .stream()
            .map(AccountWeaknessEntity::toDomain)
            .toList();
    }

    @Override
    public List<AccountWeakness> listWeaknessesBySession(Long sessionId) {
        return accountWeaknessMapper.selectList(new LambdaQueryWrapper<AccountWeaknessEntity>()
            .eq(AccountWeaknessEntity::getSessionId, sessionId)
            .orderByAsc(AccountWeaknessEntity::getCreatedAt)
            .orderByAsc(AccountWeaknessEntity::getId))
            .stream()
            .map(AccountWeaknessEntity::toDomain)
            .toList();
    }

    @Override
    public void replaceScore(ScoreHistory score) {
        scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistoryEntity>()
            .eq(ScoreHistoryEntity::getSessionId, score.getSessionId()));
        scoreHistoryMapper.insert(ScoreHistoryEntity.of(score));
    }

    @Override
    public void replaceWeaknesses(Long sessionId, List<AccountWeakness> weaknesses) {
        accountWeaknessMapper.delete(new LambdaQueryWrapper<AccountWeaknessEntity>()
            .eq(AccountWeaknessEntity::getSessionId, sessionId));
        for (AccountWeakness weakness : weaknesses) {
            accountWeaknessMapper.insert(AccountWeaknessEntity.of(weakness));
        }
    }
}
