package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.SecurityGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.*;
import com.aiworkmate.service.AccessGovernanceQueryService;
import com.aiworkmate.service.AiOperationPermissionQueryService;
import com.aiworkmate.service.DataPermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityGovernanceAgentDomainToolAdapterTest {
    private final AccessGovernanceQueryService accessService = mock(AccessGovernanceQueryService.class);
    private final DataPermissionService dataPermissionService = mock(DataPermissionService.class);
    private final AiOperationPermissionQueryService aiPermissionService = mock(AiOperationPermissionQueryService.class);
    private final SecurityGovernanceAgentDomainToolAdapter adapter =
            new SecurityGovernanceAgentDomainToolAdapter(accessService, dataPermissionService, aiPermissionService);
    private final ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");

    @Test
    void accessOverviewReturnsCountsWithoutIdentityOrPermissionDetails() {
        when(accessService.overview(2L)).thenReturn(new AccessControlOverviewResponse(
                List.of(new AccessUserResponse(99L, "Secret User", "secret@example.com", "ADMIN",
                        List.of("ADMIN"), 1, 88L, null, null, 1L, null, null)),
                List.of(new AccessRoleResponse("ADMIN", "Admin", "desc", true, List.of("secret:grant"))),
                List.of(new AccessPermissionResponse("secret:grant", "Secret", "security", "desc")),
                List.of(), List.of(), List.of()));

        var result = adapter.accessOverview(actor, new SecurityGovernanceToolPort.AccessQuery(null));

        assertThat(result.userCount()).isEqualTo(1);
        assertThat(result.roles().get(0).permissionCount()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("Secret User", "secret@example.com", "secret:grant", "99", "88");
    }

    @Test
    void dataScopeOverviewReturnsDepartmentCountWithoutBindingsOrIds() {
        when(dataPermissionService.overview(2L)).thenReturn(new DataPermissionOverviewResponse(
                List.of(new DataPermissionPolicyResponse(7L, "Tenant", "desc", "DEPARTMENT",
                        List.of(88L, 89L), true, 3L, LocalDateTime.of(2026, 9, 15, 10, 0))),
                List.of(), List.of(), List.of(),
                List.of(new DataPermissionRoleBindingResponse("ADMIN", 7L)),
                List.of(new DataPermissionUserExceptionResponse(99L, 7L))));

        var result = adapter.dataScopes(actor, new SecurityGovernanceToolPort.DataScopeQuery(null, null));

        assertThat(result.policies().get(0).departmentCount()).isEqualTo(2);
        assertThat(result.roleBindingCount()).isEqualTo(1);
        assertThat(result.userExceptionCount()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("88", "89", "99", "ADMIN");
    }

    @Test
    void aiPolicyOverviewReturnsEligibilityCountsWithoutGrantLists() {
        when(aiPermissionService.overview(2L)).thenReturn(new AiOperationPermissionOverviewResponse(
                new AiOperationPermissionOverviewResponse.RuntimeStatus(true, true, true, false),
                new AiOperationPermissionOverviewResponse.TenantPolicy(true, false),
                List.of(),
                List.of(new AiOperationPermissionOverviewResponse.Role("ADMIN", "Admin", "desc", true,
                        false, List.of("secret.tool", "safe.tool"), List.of("secret.tool")))));

        var result = adapter.aiPolicies(actor, new SecurityGovernanceToolPort.AiPolicyQuery(null, null, null));

        assertThat(result.roles().get(0).eligibleToolCount()).isEqualTo(2);
        assertThat(result.roles().get(0).grantedToolCount()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("secret.tool", "safe.tool");
    }
}
