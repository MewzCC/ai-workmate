package com.aiworkmate.service;

import com.aiworkmate.dto.PlatformObservabilityResponse;

public interface PlatformObservabilityService {
    PlatformObservabilityResponse overview(Long userId, String range);
}
