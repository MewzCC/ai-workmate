package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetRepairStartToolHandlerTest {
    @Test
    void mapsBoundedArgumentsAndUsesStableOperationKey() throws Exception {
        AssetToolPort port = mock(AssetToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 22L, 1, "trace");
        var command = new AssetToolPort.RepairStartCommand(9, 4, "电源故障送修");
        var operationKey = new ToolOperationKey("agent:10:22:asset.repair.start:v1");
        when(port.startRepair(eq(context.actor()), eq(command), eq(operationKey)))
                .thenReturn(new AssetToolPort.RepairStartResult(9, "REPAIRING", 5));

        var output = new AssetRepairStartToolHandler(port, mapper).execute(context, mapper.readTree(
                "{\"assetId\":9,\"version\":4,\"reason\":\"电源故障送修\"}"));

        assertThat(output.path("status").asText()).isEqualTo("REPAIRING");
        verify(port).startRepair(context.actor(), command, operationKey);
    }
}
