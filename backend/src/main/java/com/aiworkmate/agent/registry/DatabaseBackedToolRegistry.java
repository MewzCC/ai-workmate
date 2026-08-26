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
            "my-applications", Set.of("leave.mine", "leave.createDraft"),
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
                .map(definition -> resolveExecutableTool(access.tenantId(), definition.code()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(definition -> hasPermissions(access.permissions(), definition))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ToolDefinition> resolveExecutableTool(Long tenantId, String toolCode) {
        AgentTenantPolicy policy = tenantId == null ? null : tenantPolicyMapper.selectById(tenantId);
        if (tenantId == null || toolCode == null || !properties.isEnabled()
                || policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            return Optional.empty();
        }
        ToolDefinition definition = catalog.find(toolCode).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        if (definition.sideEffect() != SideEffect.NONE
                && (!properties.isWriteToolsEnabled() || !Boolean.TRUE.equals(policy.getWriteToolsEnabled()))) {
            return Optional.empty();
        }
        AgentTool platform = toolMapper.selectPlatformTool(toolCode);
        ToolDefinition effective = platform != null && platform.getTenantId() == null ? narrow(definition, platform) : null;
        if (effective == null) {
            return Optional.empty();
        }
        AgentTool tenant = toolMapper.selectTenantTool(tenantId, toolCode);
        if (tenant == null) {
            return Optional.of(effective);
        }
        try {
            return tenantId.equals(tenant.getTenantId())
                    ? Optional.ofNullable(narrow(effective, tenant))
                    : Optional.empty();
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

    private ToolDefinition narrow(ToolDefinition definition, AgentTool row) {
        if (row == null || !Boolean.TRUE.equals(row.getEnabled()) || !definition.code().equals(row.getCode())) {
            return null;
        }
        try {
            RiskLevel risk = RiskLevel.valueOf(row.getRiskLevel());
            ConfirmationPolicy confirmation = ConfirmationPolicy.valueOf(row.getConfirmationPolicy());
            PermissionMode permissionMode = PermissionMode.valueOf(row.getPermissionMode());
            RetryPolicy retryPolicy = RetryPolicy.valueOf(row.getRetryPolicy());
            Set<String> permissions = jsonPermissions(row.getRequiredPermissions());
            boolean valid = definition.handlerVersion().equals(row.getHandlerVersion())
                    && definition.schemaHash().equals(row.getSchemaHash())
                    && risk.ordinal() >= definition.riskLevel().ordinal()
                    && strongerPermissionMode(definition.permissionMode(), permissionMode)
                    && definition.ownershipPolicy().name().equals(row.getDataScopePolicy())
                    && strongerRetryPolicy(definition.retryPolicy(), retryPolicy)
                    && definition.sideEffect().name().equals(row.getSideEffect())
                    && confirmation.ordinal() >= definition.confirmationPolicy().ordinal()
                    && confirmationForRisk(risk, confirmation)
                    && row.getMaxResultItems() <= definition.maxResultItems()
                    && row.getMaxResultBytes() <= definition.maxResultBytes()
                    && row.getTimeoutMs() <= definition.timeoutMs()
                    && permissions.containsAll(definition.requiredPermissions());
            if (!valid) {
                return null;
            }
            ToolDefinition narrowed = new ToolDefinition(
                    definition.code(), definition.name(), definition.description(), definition.purpose(),
                    definition.handlerVersion(), definition.inputSchema(), definition.outputSchema(), definition.schemaHash(),
                    risk, permissions, permissionMode, definition.ownershipPolicy(), retryPolicy,
                    definition.sideEffect(), confirmation, row.getMaxResultItems(), row.getMaxResultBytes(),
                    row.getTimeoutMs(), definition.auditPolicy()
            );
            narrowed.validate();
            return narrowed;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean strongerPermissionMode(PermissionMode baseline, PermissionMode candidate) {
        return baseline == candidate || (baseline == PermissionMode.ANY && candidate == PermissionMode.ALL);
    }

    private boolean strongerRetryPolicy(RetryPolicy baseline, RetryPolicy candidate) {
        return baseline == candidate || candidate == RetryPolicy.NEVER;
    }

    private boolean confirmationForRisk(RiskLevel risk, ConfirmationPolicy confirmation) {
        return switch (risk) {
            case L0 -> true;
            case L1 -> confirmation.ordinal() >= ConfirmationPolicy.EXPLICIT.ordinal();
            case L2 -> confirmation == ConfirmationPolicy.SECONDARY;
        };
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
