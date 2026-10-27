package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AgentTaskCenterToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentTaskCancelToolHandlerTest {
    @Test
    void mapsOnlyBoundedTaskIdentifierAndTrustedActor() throws Exception {
        var port = mock(AgentTaskCenterToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        var command = new AgentTaskCenterToolPort.CancelCommand("agt-target");
        var updatedAt = LocalDateTime.of(2026, 10, 1, 19, 45);
        when(port.cancel(context.actor(), command)).thenReturn(
                new AgentTaskCenterToolPort.CancelResult("agt-target", "CANCELLED", updatedAt));

        var result = new AgentTaskCancelToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"taskId\":\"agt-target\"}"));

        assertThat(result.path("taskId").asText()).isEqualTo("agt-target");
        assertThat(result.path("status").asText()).isEqualTo("CANCELLED");
        verify(port).cancel(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
