package com.aiworkmate.service;

import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;

public interface ObservabilityPreferenceService {
    ObservabilityPreferenceResponse preferences(Long userId);
    ObservabilityPreferenceResponse update(Long userId, ObservabilityPreferenceRequest request);
}
