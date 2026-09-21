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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatabaseBackedToolRegistry implements ToolRegistry {

    private final AgentRuntimeProperties properties;
    private final AgentToolMapper toolMapper;
    private final AgentTenantPolicyMapper tenantPolicyMapper;
    private final ToolCatalog catalog;
    private final PageActionPolicyResolver pageActionPolicyResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ToolDefinition> resolveAllowedTools(ResolvedUserAccess access, String pageId) {
        if (access == null || !properties.isEnabled() || !properties.isPlanningEnabled()) {
            return List.of();
        }
        AgentTenantPolicy policy = tenantPolicyMapper.selectById(access.tenantId());
        if (!tenantEnabled(policy)) return List.of();
        Set<String> pageTools = pageActionPolicyResolver.enabledToolCodes(access.tenantId(), pageId);
        if (pageTools.isEmpty()) return List.of();

        Map<String, AgentTool> platformRows = index(toolMapper.selectPlatformTools(), null);
        Map<String, AgentTool> tenantRows = index(toolMapper.selectTenantTools(access.tenantId()), access.tenantId());
        return catalog.all().stream()
                .filter(definition -> pageTools.contains(definition.code()))
                .map(definition -> resolveDefinition(policy, definition,
                        platformRows.get(definition.code()), tenantRows.get(definition.code())).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(definition -> hasPermissions(access.permissions(), definition))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ToolDefinition> resolveExecutableTool(Long tenantId, String toolCode) {
        return resolveAvailability(tenantId, toolCode).definition();
    }

    @Override
    @Transactional(readOnly = true)
    public ToolAvailability resolveAvailability(Long tenantId, String toolCode) {
        if (tenantId == null || toolCode == null) {
            return ToolAvailability.unavailable(ToolAvailability.Status.UNAVAILABLE);
        }
        AgentTenantPolicy policy = tenantPolicyMapper.selectById(tenantId);
        if (!properties.isEnabled() || !tenantEnabled(policy)) {
            return ToolAvailability.unavailable(ToolAvailability.Status.DISABLED);
        }
        ToolDefinition definition = catalog.find(toolCode).orElse(null);
        if (definition == null) {
            return ToolAvailability.unavailable(ToolAvailability.Status.UNAVAILABLE);
        }
        if (!writeEnabled(policy, definition)) {
            return ToolAvailability.unavailable(ToolAvailability.Status.DISABLED);
        }
        AgentTool platform = toolMapper.selectPlatformTool(toolCode);
        AgentTool tenant = toolMapper.selectTenantTool(tenantId, toolCode);
        return resolveDefinition(policy, definition, platform, tenant)
                .map(ToolAvailability::available)
                .orElseGet(() -> ToolAvailability.unavailable(ToolAvailability.Status.UNAVAILABLE));
    }

    private Optional<ToolDefinition> resolveDefinition(AgentTenantPolicy policy, ToolDefinition definition,
                                                       AgentTool platform, AgentTool tenant) {
        if (!writeEnabled(policy, definition) || platform == null || platform.getTenantId() != null) {
            return Optional.empty();
        }
        try {
            ToolDefinition effective = narrow(definition, platform);
            if (effective == null) return Optional.empty();
            if (tenant == null) return Optional.of(effective);
            if (!policy.getTenantId().equals(tenant.getTenantId())) return Optional.empty();
            return Optional.ofNullable(narrow(effective, tenant));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, AgentTool> index(List<AgentTool> rows, Long expectedTenantId) {
        if (rows == null) return Map.of();
        return rows.stream()
                .filter(row -> row != null && java.util.Objects.equals(expectedTenantId, row.getTenantId()))
                .collect(Collectors.toUnmodifiableMap(AgentTool::getCode, Function.identity(), (left, right) -> left));
    }

    private boolean tenantEnabled(AgentTenantPolicy policy) {
        return policy != null && policy.getTenantId() != null && Boolean.TRUE.equals(policy.getEnabled());
    }

    private boolean writeEnabled(AgentTenantPolicy policy, ToolDefinition definition) {
        return definition.sideEffect() == SideEffect.NONE
                || (properties.isWriteToolsEnabled() && Boolean.TRUE.equals(policy.getWriteToolsEnabled()));
    }

    private boolean hasPermissions(List<String> permissions, ToolDefinition definition) {
        boolean businessPermission = definition.permissionMode() == PermissionMode.ALL
                ? permissions.containsAll(definition.requiredPermissions())
                : definition.requiredPermissions().stream().anyMatch(permissions::contains);
        return businessPermission && permissions.contains(permissionCode(definition.code()));
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
