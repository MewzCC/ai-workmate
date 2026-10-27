package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IntegrationEndpointUpdateStatusToolHandlerTest {
    @Test
    void dispatchesOneVersionedStatusTransitionWithGatewayActor() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var port = mock(PlatformOperationsToolPort.class);
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");
        var command = new PlatformOperationsToolPort.EndpointStatusCommand(12L, 3, "ACTIVE");
        when(port.updateEndpointStatus(context.actor(), command)).thenReturn(
                new PlatformOperationsToolPort.EndpointStatusResult(12L, "HEALTH", "ACTIVE", 4,
                        LocalDateTime.of(2026, 10, 8, 23, 59)));

        var handler = new IntegrationEndpointUpdateStatusToolHandler(port, mapper);
        var result = handler.execute(context, mapper.readTree(
                "{\"endpointId\":12,\"version\":3,\"status\":\"ACTIVE\"}"));

        assertThat(handler.toolCode()).isEqualTo(ToolCode.INTEGRATION_ENDPOINT_UPDATE_STATUS.code());
        assertThat(handler.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("status").asText()).isEqualTo("ACTIVE");
        verify(port).updateEndpointStatus(context.actor(), command);
    }
}
