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
    @Test void governanceCollectionsAreBoundedBeforeSerialization() {
        var tools = java.util.stream.IntStream.range(0,140).mapToObj(index ->
                new AiOperationPermissionOverviewResponse.Tool("tool."+index,"Tool "+index,"", "L0","NONE","NONE",List.of(),List.of("ai-permission"),true,true,true)).toList();
        var roles = java.util.stream.IntStream.range(0,61).mapToObj(index ->
                new AiOperationPermissionOverviewResponse.Role("ROLE_"+index,"Role "+index,"",false,false,List.of(),List.of())).toList();
        when(aiPermissionService.overview(2L)).thenReturn(new AiOperationPermissionOverviewResponse(
                new AiOperationPermissionOverviewResponse.RuntimeStatus(true,true,true,false),
                new AiOperationPermissionOverviewResponse.TenantPolicy(true,false),tools,roles));
        var result = adapter.aiPolicies(actor,new SecurityGovernanceToolPort.AiPolicyQuery(null,null,null));
        assertThat(result.tools()).hasSize(50);
        assertThat(result.roles()).hasSize(50);
        assertThat(result.toolCount()).isEqualTo(140);
        assertThat(result.roleCount()).isEqualTo(61);
        assertThat(result.toolsTruncated()).isTrue();
        assertThat(result.rolesTruncated()).isTrue();
        var filtered = adapter.aiPolicies(actor,new SecurityGovernanceToolPort.AiPolicyQuery("tool.99","ROLE_60",null));
        assertThat(filtered.toolCount()).isEqualTo(1);
        assertThat(filtered.roleCount()).isEqualTo(1);
        assertThat(filtered.toolsTruncated()).isFalse();
        assertThat(filtered.rolesTruncated()).isFalse();
    }
    private final AccessGovernanceQueryService accessService = mock(AccessGovernanceQueryService.class);
    private final DataPermissionService dataPermissionService = mock(DataPermissionService.class);
    private final AiOperationPermissionQueryService aiPermissionService = mock(AiOperationPermissionQueryService.class);
    private final SecurityGovernanceAgentDomainToolAdapter adapter =
            new SecurityGovernanceAgentDomainToolAdapter(accessService, dataPermissionService, aiPermissionService);
    private final ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");

    @Test void accessAndDataScopesUseFilteredTotalsAndStableBoundedLists() {
        var roles = java.util.stream.IntStream.rangeClosed(1,61).mapToObj(i ->
                new AccessRoleResponse("R"+i,"Role "+i,"",false,List.<String>of())).toList();
        when(accessService.overview(2L)).thenReturn(new AccessControlOverviewResponse(List.of(),roles,List.of(),List.of(),List.of(),List.of()));
        var access = adapter.accessOverview(actor,new SecurityGovernanceToolPort.AccessQuery(null));
        assertThat(access.roles()).hasSize(50).extracting(SecurityGovernanceToolPort.Role::code).isSorted();
        assertThat(access.roleCount()).isEqualTo(61);
        assertThat(access.rolesTruncated()).isTrue();
        var filtered = adapter.accessOverview(actor,new SecurityGovernanceToolPort.AccessQuery("R61"));
        assertThat(filtered.roleCount()).isEqualTo(1);
        assertThat(filtered.rolesTruncated()).isFalse();
        var policies = java.util.stream.IntStream.rangeClosed(1,61).mapToObj(i ->
                new DataPermissionPolicyResponse((long)i,"Policy "+i,"","SELF",List.of(),true,0L,null)).toList();
        when(dataPermissionService.overview(2L)).thenReturn(new DataPermissionOverviewResponse(policies,List.of(),List.of(),List.of(),List.of(),List.of()));
        var data = adapter.dataScopes(actor,new SecurityGovernanceToolPort.DataScopeQuery(null,null));
        assertThat(data.policies()).hasSize(50).extracting(SecurityGovernanceToolPort.DataScopePolicy::id).isSorted();
        assertThat(data.policyCount()).isEqualTo(61);
        assertThat(data.policiesTruncated()).isTrue();
        var empty = adapter.dataScopes(actor,new SecurityGovernanceToolPort.DataScopeQuery("ALL",null));
        assertThat(empty.policyCount()).isZero();
        assertThat(empty.policiesTruncated()).isFalse();
    }

    @Test void canonicalDepartmentTreeFilterMatchesTheDomainsLegacyScopeName() {
        when(dataPermissionService.overview(2L)).thenReturn(new DataPermissionOverviewResponse(
                List.of(new DataPermissionPolicyResponse(1L,"Tree","","DEPARTMENT_AND_CHILDREN",List.of(),true,0L,null),
                        new DataPermissionPolicyResponse(2L,"Self","","SELF",List.of(),true,0L,null)),
                List.of(),List.of(),List.of(),List.of(),List.of()));
        var result = adapter.dataScopes(actor,new SecurityGovernanceToolPort.DataScopeQuery("DEPARTMENT_TREE",null));
        assertThat(result.policies()).hasSize(1);
        assertThat(result.policies().get(0).scopeType()).isEqualTo("DEPARTMENT_AND_CHILDREN");
        assertThat(result.policyCount()).isEqualTo(1);
        assertThat(result.policiesTruncated()).isFalse();
    }

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
