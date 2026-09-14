package com.aiworkmate.dto;

public record RuntimeLogStatsResponse(
        Long total,
        Long succeeded,
        Long failed,
        Long blocked,
        Long averageDurationMs
) {
}
