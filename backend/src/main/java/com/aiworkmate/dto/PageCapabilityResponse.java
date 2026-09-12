package com.aiworkmate.dto;

import java.util.List;

public record PageCapabilityResponse(
        String pageId,
        String componentKey,
        int version,
        List<String> uiCommands,
        String dataScopePolicy,
        List<String> effectiveDataScopes,
        List<Tool> tools
) {
    public record Tool(
            String code,
            String name,
            String description,
            String riskLevel,
            String sideEffect,
            String confirmationPolicy,
            String ownershipPolicy
    ) {
    }
}
