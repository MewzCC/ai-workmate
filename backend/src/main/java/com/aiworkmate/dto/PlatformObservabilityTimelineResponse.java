package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlatformObservabilityTimelineResponse(
        LocalDateTime from,
        LocalDateTime to,
        String interval,
        List<PlatformObservabilityResponse.TimelinePoint> timeline) {
}
