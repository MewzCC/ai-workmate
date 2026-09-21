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
    private final ToolPolicySource policySource;
    private final ToolCatalog catalog;
    private final PageActionPolicyResolver pageActionPolicyResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ToolDefinition> resolveAllowedTools(ResolvedUserAccess access, String pageId) {
        if (access == null || !properties.isEnabled() || !properties.isPlanningEnabled()) {
            return List.of();
        }
        ToolPolicySource.TenantPolicy policy = policySource.tenantPolicy(access.tenantId()).orElse(null);
        if (!tenantEnabled(policy)) return List.of();
        Set<String> pageTools = pageActionPolicyResolver.enabledToolCodes(access.tenantId(), pageId);
        if (pageTools.isEmpty()) return List.of();

        Map<String, ToolPolicySource.ToolPolicy> platformRows = index(policySource.platformTools(), null);
        Map<String, ToolPolicySource.ToolPolicy> tenantRows = index(policySource.tenantTools(access.tenantId()), access.tenantId());
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
        ToolPolicySource.TenantPolicy policy = policySource.tenantPolicy(tenantId).orElse(null);
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
        ToolPolicySource.ToolPolicy platform = policySource.platformTool(toolCode).orElse(null);
        ToolPolicySource.ToolPolicy tenant = policySource.tenantTool(tenantId, toolCode).orElse(null);
        return resolveDefinition(policy, definition, platform, tenant)
                .map(ToolAvailability::available)
                .orElseGet(() -> ToolAvailability.unavailable(ToolAvailability.Status.UNAVAILABLE));
    }

    private Optional<ToolDefinition> resolveDefinition(ToolPolicySource.TenantPolicy policy,
                                                       ToolDefinition definition,
                                                       ToolPolicySource.ToolPolicy platform,
                                                       ToolPolicySource.ToolPolicy tenant) {
        if (!writeEnabled(policy, definition) || platform == null || platform.tenantId() != null) {
            return Optional.empty();
        }
        try {
            ToolDefinition effective = narrow(definition, platform);
            if (effective == null) return Optional.empty();
            if (tenant == null) return Optional.of(effective);
            if (!policy.tenantId().equals(tenant.tenantId())) return Optional.empty();
            return Optional.ofNullable(narrow(effective, tenant));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, ToolPolicySource.ToolPolicy> index(List<ToolPolicySource.ToolPolicy> rows,
                                                           Long expectedTenantId) {
        if (rows == null) return Map.of();
        return rows.stream()
                .filter(row -> row != null && java.util.Objects.equals(expectedTenantId, row.tenantId()))
                .collect(Collectors.toUnmodifiableMap(ToolPolicySource.ToolPolicy::code,
                        Function.identity(), (left, right) -> left));
    }

    private boolean tenantEnabled(ToolPolicySource.TenantPolicy policy) {
        return policy != null && policy.tenantId() != null && policy.enabled();
    }

    private boolean writeEnabled(ToolPolicySource.TenantPolicy policy, ToolDefinition definition) {
        return definition.sideEffect() == SideEffect.NONE
                || (properties.isWriteToolsEnabled() && policy.writeToolsEnabled());
    }

    private boolean hasPermissions(List<String> permissions, ToolDefinition definition) {
        boolean businessPermission = definition.permissionMode() == PermissionMode.ALL
                ? permissions.containsAll(definition.requiredPermissions())
                : definition.requiredPermissions().stream().anyMatch(permissions::contains);
        return businessPermission && permissions.contains(permissionCode(definition.code()));
    }

    private ToolDefinition narrow(ToolDefinition definition, ToolPolicySource.ToolPolicy row) {
        if (row == null || !row.enabled() || !definition.code().equals(row.code())) {
            return null;
        }
        try {
            RiskLevel risk = RiskLevel.valueOf(row.riskLevel());
            ConfirmationPolicy confirmation = ConfirmationPolicy.valueOf(row.confirmationPolicy());
            PermissionMode permissionMode = PermissionMode.valueOf(row.permissionMode());
            RetryPolicy retryPolicy = RetryPolicy.valueOf(row.retryPolicy());
            Set<String> permissions = jsonPermissions(row.requiredPermissions());
            boolean valid = definition.handlerVersion().equals(row.handlerVersion())
                    && definition.schemaHash().equals(row.schemaHash())
                    && risk.ordinal() >= definition.riskLevel().ordinal()
                    && strongerPermissionMode(definition.permissionMode(), permissionMode)
                    && definition.ownershipPolicy().name().equals(row.dataScopePolicy())
                    && strongerRetryPolicy(definition.retryPolicy(), retryPolicy)
                    && definition.sideEffect().name().equals(row.sideEffect())
                    && confirmation.ordinal() >= definition.confirmationPolicy().ordinal()
                    && confirmationForRisk(risk, confirmation)
                    && row.maxResultItems() <= definition.maxResultItems()
                    && row.maxResultBytes() <= definition.maxResultBytes()
                    && row.timeoutMs() <= definition.timeoutMs()
                    && permissions.containsAll(definition.requiredPermissions());
            if (!valid) {
                return null;
            }
            ToolDefinition narrowed = new ToolDefinition(
                    definition.code(), definition.name(), definition.description(), definition.purpose(),
                    definition.handlerVersion(), definition.inputSchema(), definition.outputSchema(), definition.schemaHash(),
                    risk, permissions, permissionMode, definition.ownershipPolicy(), retryPolicy,
                    definition.sideEffect(), confirmation, row.maxResultItems(), row.maxResultBytes(),
                    row.timeoutMs(), definition.auditPolicy()
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
