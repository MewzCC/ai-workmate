package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record PlatformObservabilityComparisonResponse(
        String range,
        Period current,
        Period previous
) {
    public record Period(LocalDateTime from, LocalDateTime to, boolean toExclusive,
                         RuntimeLogStatsResponse stats) {}
}
