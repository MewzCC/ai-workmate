package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitorLeaveToolHandlerTest {
    @Test
    void reusesClosedVisitArgumentsAndUsesStableOperationKey() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new VisitorToolPort.VisitCommand(31, 4, "前台确认离场");
        var operationKey = new ToolOperationKey("agent:10:25:visitor.leave:v1");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 11, 0);
        when(port.leave(context.actor(), command, operationKey)).thenReturn(
                new VisitorToolPort.VisitResult(31, "LEFT", 5, occurredAt));

        var output = new VisitorLeaveToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"bookingId":31,"version":4,"remark":"前台确认离场"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("LEFT");
        verify(port).leave(context.actor(), command, operationKey);
    }
}
