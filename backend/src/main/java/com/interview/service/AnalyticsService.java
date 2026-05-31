package com.interview.service;

import com.interview.dto.AnalyticsRadarResponse;
import com.interview.dto.AnalyticsTrendItemResponse;
import com.interview.dto.AnalyticsWeaknessItemResponse;

import java.util.List;

public interface AnalyticsService {

    AnalyticsRadarResponse getRadar();

    List<AnalyticsTrendItemResponse> getTrend();

    List<AnalyticsWeaknessItemResponse> getWeaknesses();
}
