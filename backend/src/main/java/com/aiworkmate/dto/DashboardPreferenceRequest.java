package com.aiworkmate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardPreferenceRequest(
        @NotEmpty @Size(max = 4) List<String> metricCodes
) {
}
