package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.tool.internal.ToolHandlerContract;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/** Code-owned safety profiles for the two Phase 2 execution shapes. */
public final class ToolDefinitionFactory {
    private static final String READ_AUDIT = "HASHED_ARGS_RESULT";
    private static final String WRITE_AUDIT = "FULL_WRITE_AUDIT";

    private ToolDefinitionFactory() { }

    public static ToolDefinition read(
            ToolCode code, String name, String description, String purpose,
            JsonNode inputSchema, JsonNode outputSchema, Set<String> requiredPermissions,
            OwnershipPolicy ownershipPolicy, int maxResultItems, int maxResultBytes, int timeoutMs) {
        return ToolDefinition.create(code, name, description, purpose, ToolHandlerContract.VERSION,
                inputSchema, outputSchema, RiskLevel.L0, requiredPermissions, PermissionMode.ALL,
                ownershipPolicy, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                maxResultItems, maxResultBytes, timeoutMs, READ_AUDIT);
    }

    public static ToolDefinition singleWrite(
            ToolCode code, String name, String description, String purpose,
            JsonNode inputSchema, JsonNode outputSchema, ToolWriteProfile profile,
            Set<String> requiredPermissions, OwnershipPolicy ownershipPolicy,
            int maxResultItems, int maxResultBytes, int timeoutMs) {
        if (profile == null) {
            throw new IllegalArgumentException("Write profile is required");
        }
        return ToolDefinition.create(code, name, description, purpose, ToolHandlerContract.VERSION,
                inputSchema, outputSchema, profile.riskLevel(), requiredPermissions, PermissionMode.ALL,
                ownershipPolicy, profile.retryPolicy(), SideEffect.SINGLE_WRITE, profile.confirmationPolicy(),
                maxResultItems, maxResultBytes, timeoutMs, WRITE_AUDIT);
    }
}
