package com.prelude.artifact.web;

import com.prelude.BusinessException;
import com.prelude.GlobalExceptionHandler;
import com.prelude.artifact.application.InsightQueryService;
import com.prelude.artifact.application.InsightRadarView;
import com.prelude.artifact.application.InsightTrendView;
import com.prelude.artifact.application.InsightWeaknessView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsControllerTest {

    private final InsightQueryService insightQueryService = mock(InsightQueryService.class);

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new AnalyticsController(insightQueryService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void radarExposesTheThreeAxesAndTheSampleSize() throws Exception {
        when(insightQueryService.getRadar())
            .thenReturn(new InsightRadarView(8.5, 7.0, 6.25, 4));

        mockMvc.perform(get("/api/analytics/radar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.technical").value(8.5))
            .andExpect(jsonPath("$.data.expression").value(7.0))
            .andExpect(jsonPath("$.data.logic").value(6.25))
            .andExpect(jsonPath("$.data.sessionCount").value(4));
    }

    @Test
    void trendMapsEverySessionPointInOrder() throws Exception {
        when(insightQueryService.getTrend()).thenReturn(List.of(
            new InsightTrendView(1L, LocalDateTime.parse("2026-09-01T08:00:00"), 8, 7, null),
            new InsightTrendView(2L, LocalDateTime.parse("2026-09-02T08:00:00"), 9, 6, 7)
        ));

        mockMvc.perform(get("/api/analytics/trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].sessionId").value(1))
            .andExpect(jsonPath("$.data[0].logic").doesNotExist())
            .andExpect(jsonPath("$.data[1].sessionId").value(2))
            .andExpect(jsonPath("$.data[1].logic").value(7));
    }

    @Test
    void weaknessesCarriesTheGroupedDescriptions() throws Exception {
        when(insightQueryService.getWeaknesses()).thenReturn(List.of(
            new InsightWeaknessView("并发", 2, List.of("未说明可见性", "未说明原子性"))
        ));

        mockMvc.perform(get("/api/analytics/weaknesses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].category").value("并发"))
            .andExpect(jsonPath("$.data[0].count").value(2))
            .andExpect(jsonPath("$.data[0].descriptions[1]").value("未说明原子性"));
    }

    @Test
    void anEmptyAccountStillGetsAnEmptyListRatherThanAnError() throws Exception {
        when(insightQueryService.getWeaknesses()).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/weaknesses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void aQueryFailureReachesTheSharedProblemEnvelope() throws Exception {
        when(insightQueryService.getRadar()).thenThrow(BusinessException.badRequest("暂无可统计的面试"));

        mockMvc.perform(get("/api/analytics/radar"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("bad_request"))
            .andExpect(jsonPath("$.detail").value("暂无可统计的面试"));
    }
}
