package com.prelude.artifact.application;

import com.prelude.identity.api.CurrentAccount;
import com.prelude.artifact.domain.ScoreHistory;
import com.prelude.artifact.domain.AccountWeakness;
import com.prelude.artifact.application.port.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {


    private final AnalyticsRepository analyticsRepository;
    private final CurrentAccount currentAccount;

    public AnalyticsRadarView getRadar() {
        List<ScoreHistory> recentScores = analyticsRepository.recentScores(currentAccountId(), 5);

        return new AnalyticsRadarView(
            average(recentScores.stream().map(ScoreHistory::getTechnicalScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getExpressionScore).toList()),
            average(recentScores.stream().map(ScoreHistory::getLogicScore).toList()),
            recentScores.size()
        );
    }

    public List<AnalyticsTrendView> getTrend() {
        List<AnalyticsTrendView> recent = analyticsRepository.recentScores(currentAccountId(), 5)
            .stream()
            .map(item -> new AnalyticsTrendView(
                item.getSessionId(),
                item.getCreatedAt(),
                item.getTechnicalScore(),
                item.getExpressionScore(),
                item.getLogicScore()
            ))
            .toList();
        return recent.reversed();
    }

    public List<AnalyticsWeaknessView> getWeaknesses() {
        List<AccountWeakness> weaknesses = analyticsRepository.listWeaknessesByAccount(currentAccountId());

        Map<String, List<AccountWeakness>> grouped = weaknesses.stream()
            .collect(Collectors.groupingBy(AccountWeakness::getCategory, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
            .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
            .map(entry -> new AnalyticsWeaknessView(
                entry.getKey(),
                entry.getValue().size(),
                entry.getValue().stream()
                    .map(AccountWeakness::getDescription)
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

    private Long currentAccountId() {
        return currentAccount.requireId();
    }
}
