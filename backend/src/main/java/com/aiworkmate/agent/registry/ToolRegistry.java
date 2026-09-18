package com.aiworkmate.agent.registry;

import com.aiworkmate.service.model.ResolvedUserAccess;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    String TOOL_PERMISSION_PREFIX = "agent:tool:";

    List<ToolDefinition> resolveAllowedTools(ResolvedUserAccess access, String pageId);

    Optional<ToolDefinition> resolveExecutableTool(Long tenantId, String toolCode);

    default ToolAvailability resolveAvailability(Long tenantId, String toolCode) {
        return resolveExecutableTool(tenantId, toolCode).map(ToolAvailability::available)
                .orElseGet(() -> ToolAvailability.unavailable(ToolAvailability.Status.UNAVAILABLE));
    }

    default String permissionCode(String toolCode) {
        return TOOL_PERMISSION_PREFIX + toolCode;
    }
}
