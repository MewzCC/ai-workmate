package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DatabaseBackedToolRegistry implements ToolRegistry {

    private static final Map<String, Set<String>> PAGE_TOOLS = Map.of(
            "todo-list", Set.of("todo.query"),
            "my-applications", Set.of("leave.mine"),
            "knowledge-base", Set.of("knowledge.search"),
            "message-center", Set.of("notification.mine"),
            "dashboard", Set.of("todo.query", "notification.mine")
    );

    private final AgentRuntimeProperties properties;
    private final AgentToolMapper toolMapper;
    private final AgentTenantPolicyMapper tenantPolicyMapper;
    private final ToolCatalog catalog;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ToolDefinition> resolveAllowedTools(ResolvedUserAccess access, String pageId) {
        if (access == null || !properties.isEnabled() || !properties.isPlanningEnabled() || !tenantEnabled(access.tenantId())) {
            return List.of();
        }
        Set<String> pageTools = "ai-workspace".equals(pageId)
                ? catalog.all().stream().map(ToolDefinition::code).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : PAGE_TOOLS.getOrDefault(pageId, Set.of());
        return catalog.all().stream()
                .filter(definition -> pageTools.contains(definition.code()))
                .filter(definition -> hasPermissions(access.permissions(), definition))
                .filter(definition -> resolveExecutableTool(access.tenantId(), definition.code()).isPresent())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ToolDefinition> resolveExecutableTool(Long tenantId, String toolCode) {
        if (tenantId == null || toolCode == null || !properties.isEnabled() || !tenantEnabled(tenantId)) {
            return Optional.empty();
        }
        ToolDefinition definition = catalog.find(toolCode).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        AgentTool platform = toolMapper.selectPlatformTool(toolCode);
        if (!matchesCodeDefinition(platform, definition)) {
            return Optional.empty();
        }
        AgentTool tenant = toolMapper.selectTenantTool(tenantId, toolCode);
        if (tenant == null) {
            return Optional.of(definition);
        }
        try {
            return tenantNarrows(platform, tenant) ? Optional.of(definition) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private boolean tenantEnabled(Long tenantId) {
        AgentTenantPolicy policy = tenantPolicyMapper.selectById(tenantId);
        return policy != null && Boolean.TRUE.equals(policy.getEnabled());
    }

    private boolean hasPermissions(List<String> permissions, ToolDefinition definition) {
        return definition.permissionMode() == PermissionMode.ALL
                ? permissions.containsAll(definition.requiredPermissions())
                : definition.requiredPermissions().stream().anyMatch(permissions::contains);
    }

    private boolean matchesCodeDefinition(AgentTool row, ToolDefinition definition) {
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
            return false;
        }
        try {
            return definition.handlerVersion().equals(row.getHandlerVersion())
                    && definition.schemaHash().equals(row.getSchemaHash())
                    && definition.riskLevel().name().equals(row.getRiskLevel())
                    && definition.permissionMode().name().equals(row.getPermissionMode())
                    && definition.ownershipPolicy().name().equals(row.getDataScopePolicy())
                    && definition.retryPolicy().name().equals(row.getRetryPolicy())
                    && definition.sideEffect().name().equals(row.getSideEffect())
                    && definition.confirmationPolicy().name().equals(row.getConfirmationPolicy())
                    && row.getMaxResultItems() <= definition.maxResultItems()
                    && row.getMaxResultBytes() <= definition.maxResultBytes()
                    && row.getTimeoutMs() <= definition.timeoutMs()
                    && jsonPermissions(row.getRequiredPermissions()).equals(definition.requiredPermissions());
        } catch (RuntimeException exception) {
            return false;
    }
}

    private boolean tenantNarrows(AgentTool platform, AgentTool tenant) {
        return Boolean.TRUE.equals(tenant.getEnabled())
                && platform.getCode().equals(tenant.getCode())
                && platform.getHandlerVersion().equals(tenant.getHandlerVersion())
                && platform.getSchemaHash().equals(tenant.getSchemaHash())
                && platform.getRiskLevel().equals(tenant.getRiskLevel())
                && platform.getPermissionMode().equals(tenant.getPermissionMode())
                && platform.getDataScopePolicy().equals(tenant.getDataScopePolicy())
                && platform.getRetryPolicy().equals(tenant.getRetryPolicy())
                && platform.getSideEffect().equals(tenant.getSideEffect())
                && platform.getConfirmationPolicy().equals(tenant.getConfirmationPolicy())
                && tenant.getMaxResultItems() <= platform.getMaxResultItems()
                && tenant.getMaxResultBytes() <= platform.getMaxResultBytes()
                && tenant.getTimeoutMs() <= platform.getTimeoutMs()
                && jsonPermissions(tenant.getRequiredPermissions()).containsAll(jsonPermissions(platform.getRequiredPermissions()));
    }

    private Set<String> jsonPermissions(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return Set.of();
            }
            java.util.Set<String> permissions = new java.util.HashSet<>();
            node.forEach(value -> permissions.add(value.asText()));
            return Set.copyOf(permissions);
        } catch (JsonProcessingException exception) {
            return Set.of();
        }
    }

}
