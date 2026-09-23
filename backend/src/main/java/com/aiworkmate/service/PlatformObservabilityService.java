package com.aiworkmate.service;

import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.dto.PlatformObservabilityTimelineResponse;

import java.time.LocalDateTime;

public interface PlatformObservabilityService {
    PlatformObservabilityResponse overview(Long userId, String range);

    PlatformObservabilityTimelineResponse timeline(Long userId, LocalDateTime from,
                                                   LocalDateTime to, String interval);
}
