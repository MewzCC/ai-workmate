package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationEndpointCreateDraftToolHandlerTest {
    @Test
    void dispatchesBoundedDraftCreationWithGatewayActor() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var port = mock(PlatformOperationsToolPort.class);
        var context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");
        var command = new PlatformOperationsToolPort.CreateEndpointCommand(
                "health", "Health", "primary-sandbox", "GET", "/v1/health", null, "check");
        when(port.createEndpointDraft(context.actor(), command)).thenReturn(
                new PlatformOperationsToolPort.CreateEndpointResult(12L, "HEALTH", "Health",
                        "primary-sandbox", "GET", "/v1/health", "check", "DRAFT", 0,
                        LocalDateTime.of(2026, 10, 8, 23, 50)));

        var handler = new IntegrationEndpointCreateDraftToolHandler(port, mapper);
        var result = handler.execute(context, mapper.readTree("""
                {"code":"health","name":"Health","upstreamCode":"primary-sandbox","method":"GET","relativePath":"/v1/health","description":"check"}
                """));

        assertThat(handler.toolCode()).isEqualTo(ToolCode.INTEGRATION_ENDPOINT_CREATE_DRAFT.code());
        assertThat(handler.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("endpointId").asLong()).isEqualTo(12L);
        assertThat(result.path("status").asText()).isEqualTo("DRAFT");
        verify(port).createEndpointDraft(context.actor(), command);
    }
}
