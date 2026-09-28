package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlatformObservabilityResponse(
        LocalDateTime from,
        LocalDateTime to,
        String interval,
        RuntimeLogStatsResponse stats,
        Long p95DurationMs,
        List<TimelinePoint> timeline,
        List<CategoryCount> sources,
        List<CategoryCount> errorCodes
) {
    public record TimelinePoint(LocalDateTime bucket, String source, Long total, Long failed, Long blocked) {}
    public record CategoryCount(String code, Long total) {}
}
