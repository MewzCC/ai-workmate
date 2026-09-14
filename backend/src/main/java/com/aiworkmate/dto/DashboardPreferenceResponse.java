package com.aiworkmate.dto;

import java.util.List;

public record DashboardPreferenceResponse(
        List<String> metricCodes,
        List<String> availableMetricCodes
) {
}
