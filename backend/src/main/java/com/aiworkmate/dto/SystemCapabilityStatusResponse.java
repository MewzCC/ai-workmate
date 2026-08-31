package com.aiworkmate.dto;

import java.util.Map;

public record SystemCapabilityStatusResponse(
        boolean enabled,
        boolean available,
        String status,
        Map<String, String> summary
) {
}
