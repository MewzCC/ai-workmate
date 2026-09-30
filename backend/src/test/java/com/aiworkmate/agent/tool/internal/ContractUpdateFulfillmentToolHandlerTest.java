package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractUpdateFulfillmentToolHandlerTest {
    @Test
    void mapsOneVersionedFulfillmentTransition() throws Exception {
        ContractToolPort port = mock(ContractToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 30, 20, 25);
        when(port.updateContractFulfillment(eq(context.actor()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContractToolPort.ContractFulfillmentResult(
                        81L, "HT-001", "ACTIVE", "BREACHED", 4, updatedAt));

        var output = new ContractUpdateFulfillmentToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"contractId":81,"version":3,"status":"BREACHED","reason":"交付逾期"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(output.path("fulfillmentStatus").asText()).isEqualTo("BREACHED");
        assertThat(output.path("version").asInt()).isEqualTo(4);
        verify(port).updateContractFulfillment(eq(context.actor()), argThat(command ->
                command.contractId() == 81L && command.version() == 3
                        && command.status().equals("BREACHED")
                        && command.reason().equals("交付逾期")));
    }
}
