package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ObservabilityThresholdPreference(
        @Min(1) @Max(1000000) Integer failedCount,
        @Min(1) @Max(1000000) Integer blockedCount,
        @Min(1) @Max(600000) Integer p95DurationMs
) {
}
