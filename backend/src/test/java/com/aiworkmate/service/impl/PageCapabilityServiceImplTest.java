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
        assertEquals(5, response.contextSchema().fields().size());
        assertEquals("status", response.contextSchema().fields().get(0).name());
        assertEquals("STRING", response.contextSchema().fields().get(0).valueType());
        assertEquals(500, response.contextSchema().fields().get(0).maxLength());
        assertEquals(1, response.tools().size());
        assertEquals("todo.query", response.tools().get(0).code());
        assertEquals("ASSIGNED_TO_SELF", response.tools().get(0).ownershipPolicy());
        assertEquals(null, response.unavailableReason());
        verify(userAccessService).resolveActiveUser(42L);
        verify(toolRegistry).resolveAllowedTools(access, "todo");
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "alice", 9L, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 3L);
    }

    @Test
    void returnsGenericReasonWithoutEnumeratingRestrictedTools() {
        var access = access(List.of("route:todo"));
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access);
        when(toolRegistry.resolveAllowedTools(access, "todo")).thenReturn(List.of());
        var response = service.resolve(42L, "todo");
        assertEquals(List.of(), response.tools());
        assertEquals(PageCapabilityResponse.UnavailableReason.NO_AVAILABLE_TOOLS, response.unavailableReason());
    }

    @Test
    void workspaceExposesOnlyToolsWhoseBusinessPageIsCurrentlyAccessible() {
        var access = access(List.of("route:ai-workspace", "route:meeting-room", "meeting:write", "asset:read"));
        var meetingBook = tool("meeting.book", SideEffect.SINGLE_WRITE, RiskLevel.L2,
                ConfirmationPolicy.SECONDARY);
        var assetQuery = tool("asset.query", SideEffect.NONE, RiskLevel.L0, ConfirmationPolicy.NONE);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access);
        when(toolRegistry.resolveAllowedTools(access, "ai-workspace"))
                .thenReturn(List.of(assetQuery, meetingBook));

        var response = service.resolve(42L, "ai-workspace");

        assertEquals(List.of("meeting.book"), response.tools().stream()
                .map(PageCapabilityResponse.Tool::code)
                .toList());
        assertEquals(null, response.unavailableReason());
    }

    @Test
    void workspaceFailsClosedWhenNoAssociatedBusinessRouteIsAccessible() {
        var access = access(List.of("route:ai-workspace", "meeting:write"));
        var meetingBook = tool("meeting.book", SideEffect.SINGLE_WRITE, RiskLevel.L2,
                ConfirmationPolicy.SECONDARY);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access);
        when(toolRegistry.resolveAllowedTools(access, "ai-workspace")).thenReturn(List.of(meetingBook));

        var response = service.resolve(42L, "ai-workspace");

        assertEquals(List.of(), response.tools());
        assertEquals(PageCapabilityResponse.UnavailableReason.NO_AVAILABLE_TOOLS, response.unavailableReason());
    }

    private ToolDefinition tool(String code, SideEffect sideEffect, RiskLevel riskLevel,
                                ConfirmationPolicy confirmationPolicy) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.code()).thenReturn(code);
        when(definition.name()).thenReturn(code);
        when(definition.description()).thenReturn(code + " description");
        when(definition.riskLevel()).thenReturn(riskLevel);
        when(definition.sideEffect()).thenReturn(sideEffect);
        when(definition.confirmationPolicy()).thenReturn(confirmationPolicy);
        when(definition.ownershipPolicy()).thenReturn(OwnershipPolicy.TENANT_SCOPED);
        return definition;
    }
}
