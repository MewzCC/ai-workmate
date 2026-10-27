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

class ContractUpdateStatusToolHandlerTest {
    @Test
    void mapsOneVersionedLifecycleTransition() throws Exception {
        ContractToolPort port = mock(ContractToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 30, 20, 15);
        when(port.updateContractStatus(eq(context.actor()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContractToolPort.ContractDraftResult(
                        81L, "HT-001", "TERMINATED", 3, updatedAt));

        var output = new ContractUpdateStatusToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"contractId":81,"version":2,"status":"TERMINATED","reason":"双方协商终止"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("TERMINATED");
        assertThat(output.path("version").asInt()).isEqualTo(3);
        verify(port).updateContractStatus(eq(context.actor()), argThat(command ->
                command.contractId() == 81L && command.version() == 2
                        && command.status().equals("TERMINATED")
                        && command.reason().equals("双方协商终止")));
    }
}
