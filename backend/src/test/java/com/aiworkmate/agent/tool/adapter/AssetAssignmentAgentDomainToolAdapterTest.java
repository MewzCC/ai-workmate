package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetAssignmentToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AssetAssignmentOption;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetAssignmentAgentDomainToolAdapterTest {
    private final AdminAssetsService assets = mock(AdminAssetsService.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final AssetAssignmentAgentDomainToolAdapter adapter = new AssetAssignmentAgentDomainToolAdapter(assets, access);
    private final ToolActorContext actor = new ToolActorContext(9, 7, 10, 20, 1, "candidate-test");

    @Test void rejectsMovedTenantsAndRevokedBusinessPageBeforeDomainReads() {
        for (var snapshot : List.of(snapshot(2L, List.of("route:asset-ledger")), snapshot(9L, List.of("asset:write")))) {
            when(access.resolveActiveUser(7L)).thenReturn(snapshot);
            assertThatThrownBy(() -> adapter.departments(actor, new AssetAssignmentToolPort.Query(null, 1, 20))).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> adapter.custodians(actor, new AssetAssignmentToolPort.CustodianQuery(5, null, 1, 20))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(assets);
    }

    @Test void passesOnlyTrustedUserAndTypedSearchToTheDomainAndProjectsMinimalIdentities() {
        when(access.resolveActiveUser(7L)).thenReturn(snapshot(9L, List.of("route:asset-ledger", "asset:write")));
        when(assets.assetDepartmentOptions(7L, 9L, "Research", 2, 20)).thenReturn(PageResponse.of(List.of(new AssetAssignmentOption(5L, "Research", null)), 21, 2, 20));
        when(assets.assetCustodianOptions(7L, 9L, 5L, "Alice", 1, 20)).thenReturn(PageResponse.of(List.of(new AssetAssignmentOption(12L, "Alice", 5L)), 1, 1, 20));
        assertThat(adapter.departments(actor, new AssetAssignmentToolPort.Query("Research", 2, 20)).items())
                .containsExactly(new AssetAssignmentToolPort.Item(5, "Research", null));
        assertThat(adapter.custodians(actor, new AssetAssignmentToolPort.CustodianQuery(5, "Alice", 1, 20)).items())
                .containsExactly(new AssetAssignmentToolPort.Item(12, "Alice", 5L));
        verify(assets).assetDepartmentOptions(7L, 9L, "Research", 2, 20);
        verify(assets).assetCustodianOptions(7L, 9L, 5L, "Alice", 1, 20);
    }

    private ResolvedUserAccess snapshot(long tenant, List<String> permissions) {
        return new ResolvedUserAccess(7L, "Synthetic", tenant, "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }
}
