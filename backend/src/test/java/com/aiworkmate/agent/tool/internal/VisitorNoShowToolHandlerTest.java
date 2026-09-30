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

class VisitorNoShowToolHandlerTest {
    @Test
    void usesTrustedOperationKeyForOneVersionBoundNoShowTransition() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new VisitorToolPort.VisitCommand(31, 2, "超过预约时间未到访");
        var operationKey = new ToolOperationKey("agent:10:25:visitor.noShow:v1");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 30, 18, 20);
        when(port.markNoShow(context.actor(), command, operationKey)).thenReturn(
                new VisitorToolPort.VisitResult(31, "NO_SHOW", 3, occurredAt));

        var output = new VisitorNoShowToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"bookingId":31,"version":2,"remark":"超过预约时间未到访"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("NO_SHOW");
        verify(port).markNoShow(context.actor(), command, operationKey);
    }
}
