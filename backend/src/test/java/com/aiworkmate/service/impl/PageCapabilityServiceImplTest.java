package com.aiworkmate.service.impl;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.registry.ConfirmationPolicy;
import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PageCapabilityServiceImplTest {
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final PageCapabilityServiceImpl service = new PageCapabilityServiceImpl(
            userAccessService, new PageCapabilityCatalog(), toolRegistry);

    @Test
    void rejectsUnknownPageBeforeLoadingUserAccess() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolve(42L, "unknown-page"));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getErrorCode(), exception.getErrorCode());
        verifyNoInteractions(userAccessService, toolRegistry);
    }

    @Test
    void rejectsPageMissingFromLiveRoutePermissions() {
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access(List.of("todo:read")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolve(42L, "todo"));

        assertEquals(ErrorCode.PERMISSION_DENIED.getErrorCode(), exception.getErrorCode());
        verifyNoInteractions(toolRegistry);
    }

    @Test
    void canonicalizesLegacyPageAndReturnsOnlyRegistryAllowedTools() {
        ResolvedUserAccess access = access(List.of("route:todo", "todo:read"));
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.code()).thenReturn("todo.query");
        when(definition.name()).thenReturn("Query my approval tasks");
        when(definition.description()).thenReturn("Only my assigned approval tasks");
        when(definition.riskLevel()).thenReturn(RiskLevel.L0);
        when(definition.sideEffect()).thenReturn(SideEffect.NONE);
        when(definition.confirmationPolicy()).thenReturn(ConfirmationPolicy.NONE);
        when(definition.ownershipPolicy()).thenReturn(OwnershipPolicy.ASSIGNED_TO_SELF);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access);
        when(toolRegistry.resolveAllowedTools(access, "todo")).thenReturn(List.of(definition));

        PageCapabilityResponse response = service.resolve(42L, "todo-list");

        assertEquals("todo", response.pageId());
        assertEquals("TODO_LIST", response.componentKey());
        assertEquals(List.of("SELF"), response.effectiveDataScopes());
        assertEquals(List.of("ui.applyFilter", "ui.navigate", "ui.openDetail", "ui.refreshPage"),
                response.uiCommands());
        assertEquals(1, response.tools().size());
        assertEquals("todo.query", response.tools().get(0).code());
        assertEquals("ASSIGNED_TO_SELF", response.tools().get(0).ownershipPolicy());
        verify(userAccessService).resolveActiveUser(42L);
        verify(toolRegistry).resolveAllowedTools(access, "todo");
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "alice", 9L, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 3L);
    }
}
