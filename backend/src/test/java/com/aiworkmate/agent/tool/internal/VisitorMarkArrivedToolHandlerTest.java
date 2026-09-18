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

class VisitorMarkArrivedToolHandlerTest {
    @Test
    void reusesClosedVisitArgumentsAndUsesStableOperationKey() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new VisitorToolPort.VisitCommand(31, 3, "前台确认到访");
        var operationKey = new ToolOperationKey("agent:10:25:visitor.markArrived:v1");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        when(port.markArrived(context.actor(), command, operationKey)).thenReturn(
                new VisitorToolPort.VisitResult(31, "VISITED", 4, occurredAt));

        var output = new VisitorMarkArrivedToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"bookingId":31,"version":3,"remark":"前台确认到访"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("VISITED");
        verify(port).markArrived(context.actor(), command, operationKey);
    }
}
