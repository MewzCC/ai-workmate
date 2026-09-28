package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.UserPermissionToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPermissionAgentDomainToolAdapterTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final UserPermissionAgentDomainToolAdapter adapter =
            new UserPermissionAgentDomainToolAdapter(accessService);
    private final ToolActorContext actor =
            new ToolActorContext(9L, 7L, 3L, 4L, 0, "trace");

    @Test
    void resolvesLiveActorAccessThenFiltersSortsAndPaginates() {
        when(accessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "employee", 9L, "EMPLOYEE",
                List.of("REPORTER", "EMPLOYEE", "EMPLOYEE"),
                List.of("route:dashboard", "approval:read", "approval:create", "todo:read"),
                List.of("SELF", "DEPARTMENT", "SELF"), 12L));

        var result = adapter.mine(actor,
                new UserPermissionToolPort.PermissionQuery("APPROVAL", 2, 1));

        assertThat(result.primaryRole()).isEqualTo("EMPLOYEE");
        assertThat(result.roles()).containsExactly("EMPLOYEE", "REPORTER");
        assertThat(result.dataScopes()).containsExactly("DEPARTMENT", "SELF");
        assertThat(result.permissionVersion()).isEqualTo(12L);
        assertThat(result.permissions()).containsExactly("approval:read");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(2);
        verify(accessService).resolveActiveUser(7L);
    }

    @Test
    void failsClosedForMissingOrCrossTenantActor() {
        when(accessService.resolveActiveUser(7L)).thenReturn(null);
        assertThatThrownBy(() -> adapter.mine(actor,
                new UserPermissionToolPort.PermissionQuery(null, 1, 20)))
                .isInstanceOf(BusinessException.class);

        when(accessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "employee", 10L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("todo:read"), List.of("SELF"), 1L));
        assertThatThrownBy(() -> adapter.mine(actor,
                new UserPermissionToolPort.PermissionQuery(null, 1, 20)))
                .isInstanceOf(BusinessException.class);
    }
}
