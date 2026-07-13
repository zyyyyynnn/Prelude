package com.interview.insight.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;
import com.interview.insight.application.port.InsightRepository;
import com.interview.insight.infrastructure.persistence.ScoreHistoryMapper;
import com.interview.insight.infrastructure.persistence.UserWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MybatisInsightRepository implements InsightRepository {

    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;

    @Override
    public List<ScoreHistory> recentScores(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return scoreHistoryMapper.selectList(new LambdaQueryWrapper<ScoreHistory>()
            .eq(ScoreHistory::getUserId, userId)
            .orderByDesc(ScoreHistory::getCreatedAt)
            .last("LIMIT " + safeLimit));
    }

    @Override
    public List<UserWeakness> listWeaknessesByUser(Long userId) {
        return userWeaknessMapper.selectList(new LambdaQueryWrapper<UserWeakness>()
            .eq(UserWeakness::getUserId, userId)
            .orderByDesc(UserWeakness::getCreatedAt)
            .orderByAsc(UserWeakness::getId));
    }

    @Override
    public List<UserWeakness> listWeaknessesBySession(Long sessionId) {
        return userWeaknessMapper.selectList(new LambdaQueryWrapper<UserWeakness>()
            .eq(UserWeakness::getSessionId, sessionId)
            .orderByAsc(UserWeakness::getCreatedAt)
            .orderByAsc(UserWeakness::getId));
    }

    @Override
    public void replaceScore(ScoreHistory score) {
        scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
            .eq(ScoreHistory::getSessionId, score.getSessionId()));
        scoreHistoryMapper.insert(score);
    }

    @Override
    public void replaceWeaknesses(Long sessionId, List<UserWeakness> weaknesses) {
        userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
            .eq(UserWeakness::getSessionId, sessionId));
        for (UserWeakness weakness : weaknesses) {
            userWeaknessMapper.insert(weakness);
        }
    }
}
