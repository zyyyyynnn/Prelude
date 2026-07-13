package com.interview.insight.api;

import com.interview.shared.api.Result;
import com.interview.insight.api.AnalyticsRadarResponse;
import com.interview.insight.api.AnalyticsTrendItemResponse;
import com.interview.insight.api.AnalyticsWeaknessItemResponse;
import com.interview.insight.application.InsightQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final InsightQueryService insightQueryService;

    @GetMapping("/radar")
    public Result<AnalyticsRadarResponse> radar() {
        return Result.success(insightQueryService.getRadar());
    }

    @GetMapping("/trend")
    public Result<List<AnalyticsTrendItemResponse>> trend() {
        return Result.success(insightQueryService.getTrend());
    }

    @GetMapping("/weaknesses")
    public Result<List<AnalyticsWeaknessItemResponse>> weaknesses() {
        return Result.success(insightQueryService.getWeaknesses());
    }
}
