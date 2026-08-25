package com.aiworkmate.agent.registry;

import com.aiworkmate.service.model.ResolvedUserAccess;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    List<ToolDefinition> resolveAllowedTools(ResolvedUserAccess access, String pageId);

    Optional<ToolDefinition> resolveExecutableTool(Long tenantId, String toolCode);
}
