package com.aiworkmate.service;

import com.aiworkmate.dto.SystemCapabilitiesResponse;

public interface SystemCapabilityQueryService {
    SystemCapabilitiesResponse inspect(Long userId);
}
