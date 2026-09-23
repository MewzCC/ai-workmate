package com.aiworkmate.service;

import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;
import com.aiworkmate.dto.ObservabilityThresholdPreference;

public interface ObservabilityPreferenceService {
    ObservabilityPreferenceResponse preferences(Long userId);
    ObservabilityPreferenceResponse update(Long userId, ObservabilityPreferenceRequest request);
    ObservabilityThresholdPreference thresholds(Long userId);
    ObservabilityThresholdPreference updateThresholds(Long userId, ObservabilityThresholdPreference request);
}
