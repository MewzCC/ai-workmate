package com.aiworkmate.service.impl;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.AgentTenantPolicy;
import com.aiworkmate.agent.registry.AgentTenantPolicyMapper;
import com.aiworkmate.agent.registry.AgentTool;
import com.aiworkmate.agent.registry.AgentToolMapper;
import com.aiworkmate.agent.registry.PermissionMode;
import com.aiworkmate.agent.registry.PageActionCatalog;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AiOperationPermissionMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiOperationPermissionService;
import com.aiworkmate.service.BusinessAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiOperationPermissionServiceImpl implements AiOperationPermissionService {
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private final AgentRuntimeProperties runtime;
    private final ToolCatalog catalog;
    private final PageActionCatalog pageActionCatalog;
    private final AgentToolMapper toolMapper;
    private final AgentTenantPolicyMapper tenantPolicyMapper;
    private final AccessControlMapper accessControlMapper;
    private final AiOperationPermissionMapper permissionMapper;
    private final BusinessAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public AiOperationPermissionOverviewResponse overview(AuthenticatedUser operator) {
        requireOperator(operator);
        AgentTenantPolicy policy = tenantPolicyMapper.selectById(operator.tenantId());
        boolean tenantEnabled = policy != null && Boolean.TRUE.equals(policy.getEnabled());
        boolean tenantWriteEnabled = policy != null && Boolean.TRUE.equals(policy.getWriteToolsEnabled());
        Map<String, AgentTool> platformRows = toolMapper.selectPlatformTools().stream()
                .collect(Collectors.toMap(AgentTool::getCode, Function.identity()));
        Map<String, AgentTool> tenantRows = toolMapper.selectTenantTools(operator.tenantId()).stream()
                .collect(Collectors.toMap(AgentTool::getCode, Function.identity()));

        List<AiOperationPermissionOverviewResponse.Tool> tools = catalog.all().stream()
                .sorted(java.util.Comparator.comparing(ToolDefinition::code))
                .map(definition -> toTool(definition, platformRows.get(definition.code()),
                        tenantRows.get(definition.code()), tenantEnabled, tenantWriteEnabled))
                .toList();
        List<String> allToolCodes = tools.stream().map(AiOperationPermissionOverviewResponse.Tool::code).toList();
        List<AiOperationPermissionOverviewResponse.Role> roles = accessControlMapper
                .selectRolesForTenant(operator.tenantId()).stream()
                .map(role -> toRole(operator.tenantId(), role, allToolCodes))
                .toList();
        return new AiOperationPermissionOverviewResponse(
                new AiOperationPermissionOverviewResponse.RuntimeStatus(
                        runtime.isEnabled(), runtime.isPlanningEnabled(),
                        runtime.isExecutionEnabled(), runtime.isWriteToolsEnabled()),
                new AiOperationPermissionOverviewResponse.TenantPolicy(
                        tenantEnabled, tenantWriteEnabled),
                tools,
                roles
        );
    }

    @Override
    @Transactional
    public AiOperationPermissionOverviewResponse updateTenantPolicy(AuthenticatedUser operator,
                                                                     boolean enabled,
                                                                     boolean writeToolsEnabled) {
        requireOperator(operator);
        AgentTenantPolicy before = tenantPolicyMapper.selectById(operator.tenantId());
        tenantPolicyMapper.upsertSwitches(operator.tenantId(), enabled, writeToolsEnabled);
        auditService.recordTransactional(operator.tenantId(), operator.userId(), "AGENT_TENANT_POLICY",
                operator.tenantId().toString(), "UPDATE_AGENT_TENANT_POLICY", "SUCCESS",
                switches(before) + " -> " + enabled + "/" + writeToolsEnabled);
        return overview(operator);
    }

    @Override
    @Transactional
    public AiOperationPermissionOverviewResponse updateToolStatus(AuthenticatedUser operator,
                                                                   String toolCode,
                                                                   boolean enabled) {
        requireOperator(operator);
        String normalized = toolCode == null ? "" : toolCode.trim();
        ToolDefinition definition = catalog.find(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AgentTool platform = toolMapper.selectPlatformTool(normalized);
        if (platform == null || (enabled && !Boolean.TRUE.equals(platform.getEnabled()))) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        if (toolMapper.upsertTenantEnabled(operator.tenantId(), definition.code(), enabled) != 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        auditService.recordTransactional(operator.tenantId(), operator.userId(), "AGENT_TOOL",
                normalized, "UPDATE_AGENT_TOOL_STATUS", "SUCCESS", "enabled=" + enabled);
        return overview(operator);
    }

    @Override
    @Transactional
    public AiOperationPermissionOverviewResponse updateRoleTools(AuthenticatedUser operator,
                                                                 String roleCode,
                                                                 Set<String> toolCodes) {
        requireOperator(operator);
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        if (SUPER_ADMIN.equals(normalizedRole)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        if (accessControlMapper.countRoleForTenant(operator.tenantId(), normalizedRole) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Set<String> requested = new TreeSet<>(toolCodes);
        Map<String, ToolDefinition> definitions = catalog.all().stream()
                .collect(Collectors.toMap(ToolDefinition::code, Function.identity()));
        if (!definitions.keySet().containsAll(requested)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        List<String> rolePermissions = accessControlMapper.selectPermissionCodesForRoles(
                operator.tenantId(), List.of(normalizedRole));
        boolean invalidGrant = requested.stream().map(definitions::get)
                .anyMatch(definition -> !hasBusinessPermissions(rolePermissions, definition));
        if (invalidGrant) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }

        List<String> before = permissionMapper.selectRoleToolPermissions(operator.tenantId(), normalizedRole);
        permissionMapper.deleteRoleToolPermissions(operator.tenantId(), normalizedRole);
        if (!requested.isEmpty()) {
            Set<String> permissionCodes = requested.stream()
                    .map(code -> ToolRegistry.TOOL_PERMISSION_PREFIX + code)
                    .collect(Collectors.toUnmodifiableSet());
            permissionMapper.insertRoleToolPermissions(operator.tenantId(), normalizedRole, permissionCodes);
        }
        accessControlMapper.incrementPermissionVersionForRole(operator.tenantId(), normalizedRole);
        auditService.recordTransactional(operator.tenantId(), operator.userId(), "AGENT_ROLE_PERMISSION",
                normalizedRole, "UPDATE_AGENT_ROLE_TOOLS", "SUCCESS",
                before + " -> " + requested);
        return overview(operator);
    }

    private AiOperationPermissionOverviewResponse.Tool toTool(ToolDefinition definition,
                                                               AgentTool platform,
                                                               AgentTool tenant,
                                                               boolean tenantPolicyEnabled,
                                                               boolean tenantWriteEnabled) {
        boolean platformEnabled = platform != null && Boolean.TRUE.equals(platform.getEnabled());
        boolean tenantEnabled = tenant == null || Boolean.TRUE.equals(tenant.getEnabled());
        boolean writeGate = definition.sideEffect() == SideEffect.NONE || tenantWriteEnabled;
        boolean effective = runtime.isEnabled() && runtime.isPlanningEnabled() && tenantPolicyEnabled
                && platformEnabled && tenantEnabled && writeGate
                && (definition.sideEffect() == SideEffect.NONE || runtime.isWriteToolsEnabled());
        return new AiOperationPermissionOverviewResponse.Tool(
                definition.code(), definition.name(), definition.description(),
                definition.riskLevel().name(), definition.sideEffect().name(),
                definition.confirmationPolicy().name(), definition.requiredPermissions().stream().sorted().toList(),
                pageActionCatalog.all().stream()
                        .filter(binding -> binding.tool().code().equals(definition.code()))
                        .map(PageActionCatalog.Binding::pageId).sorted().toList(),
                platformEnabled, tenantEnabled, effective
        );
    }

    private AiOperationPermissionOverviewResponse.Role toRole(Long tenantId,
                                                               AccessRoleResponse role,
                                                               List<String> allToolCodes) {
        List<String> businessPermissions = SUPER_ADMIN.equals(role.code())
                ? accessControlMapper.selectAllPermissionCodesForTenant(tenantId)
                : accessControlMapper.selectPermissionCodesForRoles(tenantId, List.of(role.code()));
        List<String> eligibleToolCodes = catalog.all().stream()
                .filter(definition -> hasBusinessPermissions(businessPermissions, definition))
                .map(ToolDefinition::code)
                .sorted()
                .toList();
        List<String> codes = SUPER_ADMIN.equals(role.code())
                ? allToolCodes
                : permissionMapper.selectRoleToolPermissions(tenantId, role.code()).stream()
                    .map(code -> code.substring(ToolRegistry.TOOL_PERMISSION_PREFIX.length()))
                    .filter(allToolCodes::contains)
                    .toList();
        return new AiOperationPermissionOverviewResponse.Role(
                role.code(), role.name(), role.description(), role.builtin(),
                SUPER_ADMIN.equals(role.code()), eligibleToolCodes, codes);
    }

    private boolean hasBusinessPermissions(List<String> permissions, ToolDefinition definition) {
        return definition.permissionMode() == PermissionMode.ALL
                ? permissions.containsAll(definition.requiredPermissions())
                : definition.requiredPermissions().stream().anyMatch(permissions::contains);
    }

    private void requireOperator(AuthenticatedUser operator) {
        if (operator == null || operator.tenantId() == null || operator.userId() == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
    }

    private String switches(AgentTenantPolicy policy) {
        return policy == null ? "false/false"
                : Boolean.TRUE.equals(policy.getEnabled()) + "/"
                    + Boolean.TRUE.equals(policy.getWriteToolsEnabled());
    }
}
