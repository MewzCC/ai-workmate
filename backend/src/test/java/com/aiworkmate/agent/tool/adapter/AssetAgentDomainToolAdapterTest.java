package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.model.AssetAgentClaimCommand;
import com.aiworkmate.service.model.AssetAgentClaimReceipt;
import com.aiworkmate.service.model.AssetAgentRepairStartCommand;
import com.aiworkmate.service.model.AssetAgentRepairStartReceipt;
import com.aiworkmate.service.model.AssetAgentReturnCommand;
import com.aiworkmate.service.model.AssetAgentReturnReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssetAgentDomainToolAdapterTest {
    private final AdminAssetsService service = mock(AdminAssetsService.class);
    private final AssetAgentDomainToolAdapter adapter = new AssetAgentDomainToolAdapter(service);
    private final ToolActorContext actor = new ToolActorContext(9, 7, 10, 20, 1, "trace");

    @Test
    void mapsOnlyBoundedAssetPortFieldsUsingTrustedActor() {
        var now = LocalDateTime.of(2026, 9, 15, 9, 0);
        when(service.listAssets(7L, "Laptop", "IT", "IDLE", 2, 20)).thenReturn(PageResponse.of(List.of(
                new AssetLedgerResponse(3L, "A-3", "Laptop", "IT", "16GB", "IDLE",
                        4L, "R&D", 5L, "Alice", LocalDate.of(2026, 1, 1), new BigDecimal("8000"),
                        "ready", 2, List.of(), now, now, true, false)), 1, 2, 20));

        var result = adapter.query(actor, new AssetToolPort.Query("Laptop", "IT", "IDLE", 2, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(3);
            assertThat(item.name()).isEqualTo("Laptop");
        });
        assertThat(result.toString()).doesNotContain("tenantId", "userId");
        verify(service).listAssets(7L, "Laptop", "IT", "IDLE", 2, 20);
        verifyNoMoreInteractions(service);
    }

    @Test
    void mapsClaimToTypedDomainCommandWithoutForwardingGatewayIdentityFields() {
        var command = new AssetToolPort.ClaimCommand(3, 5, 2, "新员工领用");
        var domainCommand = new AssetAgentClaimCommand(3, 5, 2, "新员工领用");
        when(service.claimAssetAgent(7L, domainCommand, "operation"))
                .thenReturn(new AssetAgentClaimReceipt(3, "IN_USE", 3));

        assertThat(adapter.claim(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(new AssetToolPort.ClaimResult(3, "IN_USE", 3));
        verify(service).claimAssetAgent(7L, domainCommand, "operation");
        verifyNoMoreInteractions(service);
    }

    @Test
    void unobservedClaimVerificationNeverFallsBackToTheWriteMethod() {
        var command = new AssetToolPort.ClaimCommand(3, 5, 2, null);
        var domainCommand = new AssetAgentClaimCommand(3, 5, 2, null);
        when(service.findAgentAssetClaim(7L, domainCommand, "operation"))
                .thenReturn(java.util.Optional.empty());

        assertThat(adapter.findClaim(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(ToolWriteVerification.unobserved());
        verify(service).findAgentAssetClaim(7L, domainCommand, "operation");
        verifyNoMoreInteractions(service);
    }

    @Test
    void mapsReturnAndItsReadOnlyVerificationToTypedDomainContracts() {
        var command = new AssetToolPort.ReturnCommand(3, 4, "员工归还");
        var domain = new AssetAgentReturnCommand(3, 4, "员工归还");
        when(service.returnAssetAgent(7L, domain, "operation"))
                .thenReturn(new AssetAgentReturnReceipt(3, "IDLE", 5));
        when(service.findAgentAssetReturn(7L, domain, "operation"))
                .thenReturn(java.util.Optional.of(new AssetAgentReturnReceipt(3, "IDLE", 5)));

        assertThat(adapter.returnAsset(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(new AssetToolPort.ReturnResult(3, "IDLE", 5));
        assertThat(adapter.findReturn(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(ToolWriteVerification.observed(
                        new AssetToolPort.ReturnResult(3, "IDLE", 5)));
        verify(service).returnAssetAgent(7L, domain, "operation");
        verify(service).findAgentAssetReturn(7L, domain, "operation");
        verifyNoMoreInteractions(service);
    }

    @Test
    void mapsRepairStartAndItsVerificationWithoutForwardingIdentityFields() {
        var command = new AssetToolPort.RepairStartCommand(3, 5, "电源故障送修");
        var domain = new AssetAgentRepairStartCommand(3, 5, "电源故障送修");
        when(service.startAssetRepairAgent(7L, domain, "operation"))
                .thenReturn(new AssetAgentRepairStartReceipt(3, "REPAIRING", 6));
        when(service.findAgentAssetRepairStart(7L, domain, "operation"))
                .thenReturn(java.util.Optional.of(
                        new AssetAgentRepairStartReceipt(3, "REPAIRING", 6)));

        assertThat(adapter.startRepair(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(new AssetToolPort.RepairStartResult(3, "REPAIRING", 6));
        assertThat(adapter.findRepairStart(actor, command, new ToolOperationKey("operation")))
                .isEqualTo(ToolWriteVerification.observed(
                        new AssetToolPort.RepairStartResult(3, "REPAIRING", 6)));
        verify(service).startAssetRepairAgent(7L, domain, "operation");
        verify(service).findAgentAssetRepairStart(7L, domain, "operation");
        verifyNoMoreInteractions(service);
    }
}
