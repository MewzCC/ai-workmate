package com.aiworkmate.service.impl;

import com.aiworkmate.agent.registry.AgentPageActionPolicy;
import com.aiworkmate.agent.registry.AgentPageActionPolicyMapper;
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
import com.aiworkmate.dto.UpdatePageActionPolicyRequest;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageActionPolicyServiceImplTest {
    @Mock AgentPageActionPolicyMapper mapper;
    @Mock UserAccessService accessService;
    @Mock BusinessAuditService auditService;
    private PageActionPolicyServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var schema = new ObjectMapper().readTree(
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        ToolDefinition tool = ToolDefinition.create(
                "todo.query", "Todo query", "Read assigned todos", "Read-only assigned todos", "1.0.0",
                schema, schema, RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL,
                OwnershipPolicy.ASSIGNED_TO_SELF, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE,
                ConfirmationPolicy.NONE, 20, 8192, 5000, "HASHED_ARGS_RESULT");
        service = new PageActionPolicyServiceImpl(
                new PageActionCatalog(new PageCapabilityCatalog(), new ToolCatalog(List.of(tool))),
                mapper, accessService, auditService);
    }

    @Test
    void overviewUsesCodeCatalogAndDefaultsToEnabled() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:page-actions")));
        when(mapper.selectByTenant(9L)).thenReturn(List.of());

        var result = service.overview(7L);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.enabled()).isEqualTo(3);
        assertThat(result.pages()).flatExtracting(page -> page.actions())
                .allSatisfy(action -> {
                    assertThat(action.enabled()).isTrue();
                    assertThat(action.explicitlyConfigured()).isFalse();
                    assertThat(action.version()).isZero();
                });
    }

    @Test
    void rejectsUnknownBindingInsteadOfCreatingDynamicCapability() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:page-actions", "page-action:manage")));

        assertThatThrownBy(() -> service.update(7L, "dashboard", "shell.execute",
                new UpdatePageActionPolicyRequest(false, 0, "安全收紧")))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(AgentPageActionPolicy.class));
    }

    @Test
    void disablingBindingCreatesVersionedOverrideAndAudit() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:page-actions", "page-action:manage")));
        when(mapper.selectExact(9L, "dashboard", "todo.query")).thenReturn(null);
        when(mapper.selectByTenant(9L)).thenReturn(List.of());

        service.update(7L, "dashboard", "todo.query",
                new UpdatePageActionPolicyRequest(false, 0, "暂时关闭页面入口"));

        ArgumentCaptor<AgentPageActionPolicy> captor = ArgumentCaptor.forClass(AgentPageActionPolicy.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        verify(auditService).recordTransactional(9L, 7L, "AGENT_PAGE_ACTION",
                "dashboard:todo.query", "UPDATE_PAGE_ACTION_POLICY", "SUCCESS",
                "enabled=false; reason=暂时关闭页面入口");
    }

    @Test
    void rejectsStaleVersion() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:page-actions", "page-action:manage")));
        AgentPageActionPolicy existing = new AgentPageActionPolicy();
        existing.setVersion(4);
        when(mapper.selectExact(9L, "dashboard", "todo.query")).thenReturn(existing);

        assertThatThrownBy(() -> service.update(7L, "dashboard", "todo.query",
                new UpdatePageActionPolicyRequest(true, 3, "恢复页面入口")))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateEnabled(any(), any(), any(), anyBoolean(), any(), any());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "admin", 9L, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"),
                permissions, List.of("TENANT"), 1L);
    }
}
