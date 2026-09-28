package com.aiworkmate.agent.registry;

import java.util.List;
import java.util.Optional;

/**
 * Read-only policy boundary used by capability resolution and execution checks.
 * A local MyBatis adapter is used today; a remote policy service can implement
 * the same transport-neutral contract after a Spring Cloud split.
 */
public interface ToolPolicySource {
    Optional<TenantPolicy> tenantPolicy(Long tenantId);

    Optional<ToolPolicy> platformTool(String toolCode);

    Optional<ToolPolicy> tenantTool(Long tenantId, String toolCode);

    List<ToolPolicy> platformTools();

    List<ToolPolicy> tenantTools(Long tenantId);

    record TenantPolicy(Long tenantId, boolean enabled, boolean writeToolsEnabled) { }

    record ToolPolicy(
            Long tenantId,
            String code,
            String handlerVersion,
            String schemaHash,
            String riskLevel,
            String requiredPermissions,
            String permissionMode,
            String dataScopePolicy,
            String retryPolicy,
            String sideEffect,
            String confirmationPolicy,
            int maxResultItems,
            int maxResultBytes,
            int timeoutMs,
            boolean enabled
    ) { }
}
