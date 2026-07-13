package com.interview.insight.application;

import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.insight.domain.ScoreHistory;
import com.interview.insight.domain.UserWeakness;
import com.interview.insight.application.port.InsightRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightQueryServiceTest {

    private final InsightRepository repository = mock(InsightRepository.class);
    private final InsightQueryService service = new InsightQueryService(repository);

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUserId(42L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void radarAveragesNullableRecentScores() {
        when(repository.recentScores(42L, 5)).thenReturn(List.of(
            score(1L, 8, 7, 9, LocalDateTime.of(2026, 7, 11, 10, 0)),
            score(2L, 6, null, 7, LocalDateTime.of(2026, 7, 12, 10, 0))
        ));

        var radar = service.getRadar();

        assertThat(radar.technical()).isEqualTo(7.0);
        assertThat(radar.expression()).isEqualTo(7.0);
        assertThat(radar.logic()).isEqualTo(8.0);
        assertThat(radar.sessionCount()).isEqualTo(2);
    }

    @Test
    void trendReturnsChronologicalOrder() {
        ScoreHistory older = score(1L, 6, 7, 8, LocalDateTime.of(2026, 7, 11, 10, 0));
        ScoreHistory newer = score(2L, 8, 9, 7, LocalDateTime.of(2026, 7, 12, 10, 0));
        when(repository.recentScores(42L, 5)).thenReturn(List.of(newer, older));

        assertThat(service.getTrend()).extracting(item -> item.sessionId())
            .containsExactly(1L, 2L);
    }

    @Test
    void weaknessesGroupSortAndDeduplicateDescriptions() {
        when(repository.listWeaknessesByUser(42L)).thenReturn(List.of(
            weakness("并发", "锁粒度"),
            weakness("并发", "锁粒度"),
            weakness("并发", " "),
            weakness("表达", "结构化表达")
        ));

        var result = service.getWeaknesses();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().category()).isEqualTo("并发");
        assertThat(result.getFirst().count()).isEqualTo(3);
        assertThat(result.getFirst().descriptions()).containsExactly("锁粒度");
    }

    @Test
    void requiresAuthenticatedUser() {
        UserContext.remove();
        assertThatThrownBy(service::getRadar)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("请先登录");
    }

    private ScoreHistory score(Long sessionId, Integer technical, Integer expression, Integer logic, LocalDateTime at) {
        ScoreHistory score = new ScoreHistory();
        score.setSessionId(sessionId);
        score.setTechnicalScore(technical);
        score.setExpressionScore(expression);
        score.setLogicScore(logic);
        score.setCreatedAt(at);
        return score;
    }

    private UserWeakness weakness(String category, String description) {
        UserWeakness weakness = new UserWeakness();
        weakness.setCategory(category);
        weakness.setDescription(description);
        return weakness;
    }
}
