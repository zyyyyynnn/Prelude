package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.AnalyticsRadarResponse;
import com.interview.dto.AnalyticsTrendItemResponse;
import com.interview.dto.AnalyticsWeaknessItemResponse;
import com.interview.entity.ScoreHistory;
import com.interview.entity.UserWeakness;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserWeaknessMapper;
import com.interview.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;

    @Override
    public AnalyticsRadarResponse getRadar() {
        List<ScoreHistory> recentScores = scoreHistoryMapper.selectList(new LambdaQueryWrapper<ScoreHistory>()
            .eq(ScoreHistory::getUserId, currentUserId())
            .orderByDesc(ScoreHistory::getCreatedAt)
            .last("LIMIT 10"));

        return new AnalyticsRadarResponse(
            average(recentScores.stream().map(ScoreHistory::getTechnicalScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getExpressionScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getLogicScore).toList()),
            recentScores.size()
        );
    }

    @Override
    public List<AnalyticsTrendItemResponse> getTrend() {
        return scoreHistoryMapper.selectList(new LambdaQueryWrapper<ScoreHistory>()
                .eq(ScoreHistory::getUserId, currentUserId())
                .orderByAsc(ScoreHistory::getCreatedAt))
            .stream()
            .map(item -> new AnalyticsTrendItemResponse(
                item.getSessionId(),
                item.getCreatedAt(),
                item.getTechnicalScore(),
                item.getExpressionScore(),
                item.getLogicScore()
            ))
            .toList();
    }

    @Override
    public List<AnalyticsWeaknessItemResponse> getWeaknesses() {
        List<UserWeakness> weaknesses = userWeaknessMapper.selectList(new LambdaQueryWrapper<UserWeakness>()
            .eq(UserWeakness::getUserId, currentUserId())
            .orderByDesc(UserWeakness::getCreatedAt)
            .orderByAsc(UserWeakness::getId));

        Map<String, List<UserWeakness>> grouped = weaknesses.stream()
            .collect(Collectors.groupingBy(UserWeakness::getCategory, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
            .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
            .map(entry -> new AnalyticsWeaknessItemResponse(
                entry.getKey(),
                entry.getValue().size(),
                entry.getValue().stream()
                    .map(UserWeakness::getDescription)
                    .filter(description -> description != null && !description.isBlank())
                    .distinct()
                    .toList()
            ))
            .toList();
    }

    private double average(List<Integer> scores) {
        return scores.stream()
            .filter(score -> score != null)
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }
}
