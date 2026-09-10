package com.aiworkmate.dto;

import java.util.List;

public record AiOperationPermissionOverviewResponse(
        RuntimeStatus runtime,
        TenantPolicy tenantPolicy,
        List<Tool> tools,
        List<Role> roles
) {
    public record RuntimeStatus(boolean agentEnabled, boolean planningEnabled,
                                boolean executionEnabled, boolean writeToolsEnabled) {}

    public record TenantPolicy(boolean enabled, boolean writeToolsEnabled) {}

    public record Tool(String code, String name, String description, String riskLevel,
                       String sideEffect, String confirmationPolicy, List<String> requiredPermissions,
                       List<String> pageIds, boolean platformEnabled, boolean tenantEnabled,
                       boolean effectiveEnabled) {}

    public record Role(String code, String name, String description, boolean builtin,
                       boolean immutable, List<String> eligibleToolCodes, List<String> toolCodes) {}
}
