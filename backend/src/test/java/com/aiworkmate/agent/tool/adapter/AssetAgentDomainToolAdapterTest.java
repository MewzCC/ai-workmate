package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.service.AdminAssetsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssetAgentDomainToolAdapterTest {
    @Test
    void mapsOnlyBoundedAssetPortFieldsUsingTrustedActor() {
        var service = mock(AdminAssetsService.class);
        var adapter = new AssetAgentDomainToolAdapter(service);
        var actor = new ToolActorContext(9, 7, 10, 20, 1, "trace");
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
}
