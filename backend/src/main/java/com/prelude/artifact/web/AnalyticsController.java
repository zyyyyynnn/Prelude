package com.prelude.artifact.web;

import com.prelude.Result;
import com.prelude.artifact.api.AnalyticsRadarResponse;
import com.prelude.artifact.api.AnalyticsTrendItemResponse;
import com.prelude.artifact.api.AnalyticsWeaknessItemResponse;
import com.prelude.artifact.application.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/radar")
    public Result<AnalyticsRadarResponse> radar() {
        var view = analyticsQueryService.getRadar();
        return Result.success(new AnalyticsRadarResponse(
            view.technical(), view.expression(), view.logic(), view.sessionCount()
        ));
    }

    @GetMapping("/trend")
    public Result<List<AnalyticsTrendItemResponse>> trend() {
        return Result.success(analyticsQueryService.getTrend().stream()
            .map(view -> new AnalyticsTrendItemResponse(
                view.sessionId(), view.createdAt(), view.technical(), view.expression(), view.logic()
            ))
            .toList());
    }

    @GetMapping("/weaknesses")
    public Result<List<AnalyticsWeaknessItemResponse>> weaknesses() {
        return Result.success(analyticsQueryService.getWeaknesses().stream()
            .map(view -> new AnalyticsWeaknessItemResponse(
                view.category(), view.count(), view.descriptions()
            ))
            .toList());
    }
}
