package com.aiworkmate.dto;

import java.util.List;

public record PageCapabilityResponse(
        String pageId,
        String componentKey,
        int version,
        List<String> uiCommands,
        String dataScopePolicy,
        List<String> effectiveDataScopes,
        ContextSchema contextSchema,
        List<Tool> tools,
        UnavailableReason unavailableReason
) {
    public enum UnavailableReason { NO_AVAILABLE_TOOLS }

    public record ContextSchema(
            int maxBytes,
            int maxDepth,
            List<ContextField> fields
    ) {
    }

    public record ContextField(
            String name,
            String valueType,
            int maxLength
    ) {
    }

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
