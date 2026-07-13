package com.interview.insight.application;

import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.insight.api.AnalyticsRadarResponse;
import com.interview.insight.api.AnalyticsTrendItemResponse;
import com.interview.insight.api.AnalyticsWeaknessItemResponse;
import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;
import com.interview.insight.application.port.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightQueryService {


    private final InsightRepository insightRepository;

    public AnalyticsRadarResponse getRadar() {
        List<ScoreHistory> recentScores = insightRepository.recentScores(currentUserId(), 5);

        return new AnalyticsRadarResponse(
            average(recentScores.stream().map(ScoreHistory::getTechnicalScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getExpressionScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getLogicScore).toList()),
            recentScores.size()
        );
    }

    public List<AnalyticsTrendItemResponse> getTrend() {
        List<AnalyticsTrendItemResponse> recent = insightRepository.recentScores(currentUserId(), 5)
            .stream()
            .map(item -> new AnalyticsTrendItemResponse(
                item.getSessionId(),
                item.getCreatedAt(),
                item.getTechnicalScore(),
                item.getExpressionScore(),
                item.getLogicScore()
            ))
            .toList();
        return recent.reversed();
    }

    public List<AnalyticsWeaknessItemResponse> getWeaknesses() {
        List<UserWeakness> weaknesses = insightRepository.listWeaknessesByUser(currentUserId());

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
