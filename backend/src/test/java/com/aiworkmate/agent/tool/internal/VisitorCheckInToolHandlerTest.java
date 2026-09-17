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

class VisitorCheckInToolHandlerTest {
    @Test
    void mapsClosedArgumentsAndUsesStableOperationKey() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 24L, 1, "trace");
        var command = new VisitorToolPort.VisitCommand(31, 2, "已核验证件");
        var operationKey = new ToolOperationKey("agent:10:24:visitor.checkIn:v1");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 8, 55);
        when(port.checkIn(context.actor(), command, operationKey)).thenReturn(
                new VisitorToolPort.VisitResult(31, "CHECKED_IN", 3, occurredAt));

        var output = new VisitorCheckInToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"bookingId":31,"version":2,"remark":"已核验证件"}
                """));

        assertThat(output.path("bookingId").asLong()).isEqualTo(31);
        assertThat(output.path("status").asText()).isEqualTo("CHECKED_IN");
        verify(port).checkIn(context.actor(), command, operationKey);
    }
}
