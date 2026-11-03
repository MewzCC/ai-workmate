package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.PageUiToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.service.PageCapabilityService;
import com.aiworkmate.service.PageUiTaskSnapshotService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PageUiAgentDomainToolAdapterTest {
    private final PageCapabilityService capabilities = mock(PageCapabilityService.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final PageUiTaskSnapshotService snapshots = mock(PageUiTaskSnapshotService.class);
    private final PageUiAgentDomainToolAdapter adapter = new PageUiAgentDomainToolAdapter(capabilities, access, snapshots);
    private final ToolActorContext actor = new ToolActorContext(1, 2, 3, 4, 0, "synthetic-ui-trace");

    @BeforeEach void setup() {
        when(access.resolveActiveUser(2L)).thenReturn(user(1L, List.of("agent:ui:prepare")));
        when(snapshots.runningSnapshot(1L, 2L, 3L)).thenReturn(
                new PageUiTaskSnapshotService.Snapshot("dictionary", "form_session_123456", 5L, 2));
        when(capabilities.resolve(2L, "dictionary")).thenReturn(capability("dictionary"));
    }
    private ResolvedUserAccess user(Long tenant, List<String> permissions) {
        return new ResolvedUserAccess(2L, "synthetic", tenant, "STAFF", List.of("STAFF"), permissions, List.of("SELF"), 1L);
    }
    private PageCapabilityResponse capability(String page) {
        return new PageCapabilityResponse(page, "DICTIONARY", 1,
                List.of("ui.navigate", "ui.applyFilter", "ui.openDetail", "ui.fillForm", "ui.previewSubmission"),
                "SELF", List.of("SELF"), new PageCapabilityResponse.ContextSchema(4096, 3,
                List.of(new PageCapabilityResponse.ContextField("keyword", "STRING", 120),
                        new PageCapabilityResponse.ContextField("view", "STRING", 40))), List.of(), null);
    }
    private PageUiToolPort.Command command(String code, String target, Object id, PageUiToolPort.Field... fields) {
        return new PageUiToolPort.Command("dictionary", code, target, id, List.of(fields));
    }
    @Test void bindsFillToFrozenFormWithoutExecutingBusinessData() {
        var result = adapter.prepare(actor, command("ui.fillForm", null, null, new PageUiToolPort.Field("name", "synthetic")));
        assertThat(result.formContext()).isEqualTo(new PageUiToolPort.FormContext("form_session_123456", 5L, 2));
        assertThat(result.code()).isEqualTo("ui.fillForm");
    }
    @Test void requiresLivePermissionAndMatchingTenantBeforeLoadingTask() {
        for (var current : List.of(user(9L, List.of("agent:ui:prepare")), user(1L, List.of()))) {
            when(access.resolveActiveUser(2L)).thenReturn(current);
            assertThatThrownBy(() -> adapter.prepare(actor, command("ui.navigate", "todo", null))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(snapshots, capabilities);
    }
    @Test void refusesCrossPageCommandOutsideWorkspace() {
        var foreign = new PageUiToolPort.Command("todo", "ui.refreshPage", null, null, List.of());
        assertThatThrownBy(() -> adapter.prepare(actor, foreign)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(capabilities);
    }
    @Test void checksNavigationTargetAndRejectsUnregisteredCommands() {
        when(capabilities.resolve(2L, "todo")).thenThrow(new BusinessException(com.aiworkmate.common.ErrorCode.PERMISSION_DENIED));
        assertThatThrownBy(() -> adapter.prepare(actor, command("ui.navigate", "todo", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adapter.prepare(actor, command("ui.submit", null, null))).isInstanceOf(BusinessException.class);
    }
    @Test void refusesAuthorityFieldsDuplicateFieldsAndUnknownFilters() {
        for (var fields : List.of(
                List.of(new PageUiToolPort.Field("tenantId", "9")),
                List.of(new PageUiToolPort.Field("keyword", "a"), new PageUiToolPort.Field("keyword", "b")),
                List.of(new PageUiToolPort.Field("unregisteredFilter", "a")))) {
            assertThatThrownBy(() -> adapter.prepare(actor,
                    new PageUiToolPort.Command("dictionary", "ui.applyFilter", null, null, fields))).isInstanceOf(BusinessException.class);
        }
    }
    @Test void allowsOnlyKnownViewsAndTaskUuidDetails() {
        assertThat(adapter.prepare(actor, command("ui.applyFilter", null, null, new PageUiToolPort.Field("view", "items"))).fields()).hasSize(1);
        assertThatThrownBy(() -> adapter.prepare(actor, command("ui.applyFilter", null, null, new PageUiToolPort.Field("view", "admin")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adapter.prepare(actor, command("ui.openDetail", null, "12345678-1234-1234-1234-123456789abc"))).isInstanceOf(BusinessException.class);
    }
    @Test void workspaceCannotBorrowThePreviousPageFormBinding() {
        when(snapshots.runningSnapshot(1L, 2L, 3L)).thenReturn(
                new PageUiTaskSnapshotService.Snapshot("ai-workspace", "form_session_123456", 5L, 2));
        assertThat(adapter.prepare(actor, command("ui.previewSubmission", null, null)).formContext()).isNull();
    }
}
