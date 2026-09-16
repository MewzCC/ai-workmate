package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/** Code-owned safety profiles for the two Phase 2 execution shapes. */
public final class ToolDefinitionFactory {
    private static final String HANDLER_VERSION = "1.0.0";
    private static final String READ_AUDIT = "HASHED_ARGS_RESULT";
    private static final String WRITE_AUDIT = "FULL_WRITE_AUDIT";

    private ToolDefinitionFactory() { }

    public static ToolDefinition read(
            ToolCode code, String name, String description, String purpose,
            JsonNode inputSchema, JsonNode outputSchema, Set<String> requiredPermissions,
            OwnershipPolicy ownershipPolicy, int maxResultItems, int maxResultBytes, int timeoutMs) {
        return ToolDefinition.create(code, name, description, purpose, HANDLER_VERSION,
                inputSchema, outputSchema, RiskLevel.L0, requiredPermissions, PermissionMode.ALL,
                ownershipPolicy, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                maxResultItems, maxResultBytes, timeoutMs, READ_AUDIT);
    }

    public static ToolDefinition singleWrite(
            ToolCode code, String name, String description, String purpose,
            JsonNode inputSchema, JsonNode outputSchema, RiskLevel riskLevel,
            Set<String> requiredPermissions, OwnershipPolicy ownershipPolicy,
            RetryPolicy retryPolicy, ConfirmationPolicy confirmationPolicy,
            int maxResultItems, int maxResultBytes, int timeoutMs) {
        return ToolDefinition.create(code, name, description, purpose, HANDLER_VERSION,
                inputSchema, outputSchema, riskLevel, requiredPermissions, PermissionMode.ALL,
                ownershipPolicy, retryPolicy, SideEffect.SINGLE_WRITE, confirmationPolicy,
                maxResultItems, maxResultBytes, timeoutMs, WRITE_AUDIT);
    }
}
