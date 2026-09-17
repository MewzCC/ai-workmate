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

class AssetReturnToolHandlerTest {
    @Test
    void mapsClosedArgumentsAndUsesStableOperationKey() throws Exception {
        AssetToolPort port = mock(AssetToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 21L, 1, "trace");
        var command = new AssetToolPort.ReturnCommand(9, 3, "员工归还");
        var operationKey = new ToolOperationKey("agent:10:21:asset.return:v1");
        when(port.returnAsset(eq(context.actor()), eq(command), eq(operationKey)))
                .thenReturn(new AssetToolPort.ReturnResult(9, "IDLE", 4));

        var output = new AssetReturnToolHandler(port, mapper).execute(context, mapper.readTree(
                "{\"assetId\":9,\"version\":3,\"reason\":\"员工归还\"}"));

        assertThat(output.path("status").asText()).isEqualTo("IDLE");
        verify(port).returnAsset(context.actor(), command, operationKey);
    }
}
