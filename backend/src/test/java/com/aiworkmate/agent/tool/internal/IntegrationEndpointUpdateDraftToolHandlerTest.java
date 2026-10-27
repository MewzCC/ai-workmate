package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IntegrationEndpointUpdateDraftToolHandlerTest {
    @Test
    void dispatchesOneVersionedDraftUpdateWithGatewayActor() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var port = mock(PlatformOperationsToolPort.class);
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");
        var command = new PlatformOperationsToolPort.UpdateEndpointCommand(
                12L, 3, "HEALTH", "Health v2", "primary-sandbox", "GET", "/v2/health", null,
                "check v2");
        when(port.updateEndpointDraft(context.actor(), command)).thenReturn(
                new PlatformOperationsToolPort.CreateEndpointResult(12L, "HEALTH", "Health v2",
                        "primary-sandbox", "GET", "/v2/health", "check v2", "DRAFT", 4,
                        LocalDateTime.of(2026, 10, 8, 23, 55)));

        var handler = new IntegrationEndpointUpdateDraftToolHandler(port, mapper);
        var result = handler.execute(context, mapper.readTree("""
                {"endpointId":12,"version":3,"code":"HEALTH","name":"Health v2","upstreamCode":"primary-sandbox","method":"GET","relativePath":"/v2/health","description":"check v2"}
                """));

        assertThat(handler.toolCode()).isEqualTo(ToolCode.INTEGRATION_ENDPOINT_UPDATE_DRAFT.code());
        assertThat(handler.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("version").asInt()).isEqualTo(4);
        assertThat(result.path("status").asText()).isEqualTo("DRAFT");
        verify(port).updateEndpointDraft(context.actor(), command);
    }
}
