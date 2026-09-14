package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlatformOperationsQueryToolHandlerTest {
    @Test
    void dispatchesFourNarrowQueriesWithGatewayDerivedActor() throws Exception {
        var mapper = new ObjectMapper();
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 5, "trace");
        PlatformOperationsToolPort port = mock(PlatformOperationsToolPort.class);
        when(port.endpoints(any(), any())).thenReturn(new PlatformOperationsToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.pageActions(any(), any())).thenReturn(new PlatformOperationsToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.runtimeLogs(any(), any())).thenReturn(new PlatformOperationsToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.replays(any(), any())).thenReturn(new PlatformOperationsToolPort.Page<>(List.of(), 0, 1, 20));
        var handlers = List.of(new IntegrationEndpointQueryToolHandler(port, mapper),
                new PageActionQueryToolHandler(port, mapper), new RuntimeLogQueryToolHandler(port, mapper),
                new SandboxReplayQueryToolHandler(port, mapper));
        assertThat(handlers).extracting(ToolHandler::toolCode).containsExactly(
                ToolCode.INTEGRATION_ENDPOINT_QUERY.code(), ToolCode.PAGE_ACTION_QUERY.code(),
                ToolCode.RUNTIME_LOG_QUERY.code(), ToolCode.SANDBOX_REPLAY_QUERY.code());
        handlers.forEach(handler -> handler.execute(context, mapper.createObjectNode()));
        var actor = new ToolActorContext(1L, 2L, 3L, 4L, 5, "trace");
        verify(port).endpoints(eq(actor), eq(new PlatformOperationsToolPort.EndpointQuery(null, null, null, 1, 20)));
        verify(port).pageActions(eq(actor), eq(new PlatformOperationsToolPort.PageActionQuery(null, null, 1, 20)));
        verify(port).runtimeLogs(eq(actor), eq(new PlatformOperationsToolPort.RuntimeLogQuery(null, null, null, null, null, null, 1, 20)));
        verify(port).replays(eq(actor), eq(new PlatformOperationsToolPort.ReplayQuery(null, null, null, 1, 20)));
    }
}
