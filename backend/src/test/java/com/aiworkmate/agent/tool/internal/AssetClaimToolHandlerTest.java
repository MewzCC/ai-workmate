package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetClaimToolHandlerTest {
    @Mock private AssetToolPort port;

    @Test
    void mapsClosedArgumentsAndUsesGatewayStableOperationKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        when(port.claim(eq(context.actor()), any(),
                eq(new ToolOperationKey("agent:10:20:asset.claim:v1"))))
                .thenReturn(new AssetToolPort.ClaimResult(9, "IN_USE", 3));

        var output = new AssetClaimToolHandler(port, mapper).execute(context, mapper.readTree(
                "{\"assetId\":9,\"employeeId\":20,\"version\":2,\"reason\":\"新员工领用\"}"));

        assertThat(output.path("assetId").asLong()).isEqualTo(9);
        assertThat(output.path("status").asText()).isEqualTo("IN_USE");
        ArgumentCaptor<AssetToolPort.ClaimCommand> command =
                ArgumentCaptor.forClass(AssetToolPort.ClaimCommand.class);
        verify(port).claim(eq(context.actor()), command.capture(),
                eq(new ToolOperationKey("agent:10:20:asset.claim:v1")));
        assertThat(command.getValue())
                .isEqualTo(new AssetToolPort.ClaimCommand(9, 20, 2, "新员工领用"));
    }
}
