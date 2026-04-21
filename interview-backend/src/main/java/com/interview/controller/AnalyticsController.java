package com.interview.controller;

import com.interview.common.Result;
import com.interview.dto.AnalyticsRadarResponse;
import com.interview.dto.AnalyticsTrendItemResponse;
import com.interview.dto.AnalyticsWeaknessItemResponse;
import com.interview.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/radar")
    public Result<AnalyticsRadarResponse> radar() {
        return Result.success(analyticsService.getRadar());
    }

    @GetMapping("/trend")
    public Result<List<AnalyticsTrendItemResponse>> trend() {
        return Result.success(analyticsService.getTrend());
    }

    @GetMapping("/weaknesses")
    public Result<List<AnalyticsWeaknessItemResponse>> weaknesses() {
        return Result.success(analyticsService.getWeaknesses());
    }
}
