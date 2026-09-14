package com.aiworkmate.service.impl;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.AgentTenantPolicyMapper;
import com.aiworkmate.agent.registry.AgentTool;
import com.aiworkmate.agent.registry.AgentToolMapper;
import com.aiworkmate.agent.registry.ConfirmationPolicy;
import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.registry.PageActionCatalog;
import com.aiworkmate.agent.registry.PermissionMode;
import com.aiworkmate.agent.registry.RetryPolicy;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AiOperationPermissionMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.BusinessAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOperationPermissionServiceImplTest {
    @Mock AgentToolMapper toolMapper;
    @Mock AgentTenantPolicyMapper tenantPolicyMapper;
    @Mock AccessControlMapper accessControlMapper;
    @Mock AiOperationPermissionMapper permissionMapper;
    @Mock BusinessAuditService auditService;
    private AiOperationPermissionServiceImpl service;
    private ToolDefinition tool;
    private final AuthenticatedUser operator = new AuthenticatedUser(
            7L, "admin", 9L, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"),
            List.of("agent-permission:manage"), List.of("ALL"), 3L);

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        var schema = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        tool = ToolDefinition.create("todo.query", "Todo query", "Query todos", "Read my todos",
                "1.0.0", schema, schema, RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL,
                OwnershipPolicy.ASSIGNED_TO_SELF, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE,
                ConfirmationPolicy.NONE, 20, 8192, 5000, "HASHED_ARGS_RESULT");
        AgentRuntimeProperties runtime = new AgentRuntimeProperties();
        runtime.setEnabled(true);
        runtime.setPlanningEnabled(true);
        ToolCatalog toolCatalog = new ToolCatalog(List.of(tool));
        service = new AiOperationPermissionServiceImpl(runtime, toolCatalog,
                new PageActionCatalog(new PageCapabilityCatalog(), toolCatalog),
                toolMapper, tenantPolicyMapper, accessControlMapper, permissionMapper, auditService);
    }

    @Test
    void rejectsToolGrantWhenRoleDoesNotHaveUnderlyingBusinessPermission() {
        when(accessControlMapper.countRoleForTenant(9L, "EMPLOYEE")).thenReturn(1);
        when(accessControlMapper.selectPermissionCodesForRoles(9L, List.of("EMPLOYEE")))
                .thenReturn(List.of("leave:read:self"));

        assertThatThrownBy(() -> service.updateRoleTools(operator, "employee", Set.of("todo.query")))
                .isInstanceOf(BusinessException.class);
        verify(permissionMapper, never()).deleteRoleToolPermissions(any(), any());
    }

    @Test
    void storesOnlyRegisteredToolPermissionAndRefreshesRoleUsers() {
        when(accessControlMapper.countRoleForTenant(9L, "EMPLOYEE")).thenReturn(1);
        when(accessControlMapper.selectPermissionCodesForRoles(9L, List.of("EMPLOYEE")))
                .thenReturn(List.of("todo:read"));
        when(accessControlMapper.selectRolesForTenant(9L)).thenReturn(List.of(
                new AccessRoleResponse("EMPLOYEE", "Employee", "Standard employee", true)));
        when(permissionMapper.selectRoleToolPermissions(9L, "EMPLOYEE"))
                .thenReturn(List.of(), List.of("agent:tool:todo.query"));
        when(toolMapper.selectPlatformTools()).thenReturn(List.of(platformRow()));
        when(toolMapper.selectTenantTools(9L)).thenReturn(List.of());

        var result = service.updateRoleTools(operator, "employee", Set.of("todo.query"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> codes = ArgumentCaptor.forClass(Set.class);
        verify(permissionMapper).insertRoleToolPermissions(eq(9L), eq("EMPLOYEE"), codes.capture());
        assertThat(codes.getValue()).containsExactly("agent:tool:todo.query");
        verify(accessControlMapper).incrementPermissionVersionForRole(9L, "EMPLOYEE");
        assertThat(result.roles().get(0).toolCodes()).containsExactly("todo.query");
    }

    private AgentTool platformRow() {
        AgentTool row = new AgentTool();
        row.setCode(tool.code());
        row.setEnabled(true);
        return row;
    }
}
