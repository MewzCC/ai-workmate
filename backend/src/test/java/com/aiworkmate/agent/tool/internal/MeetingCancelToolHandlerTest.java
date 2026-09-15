package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeetingCancelToolHandlerTest {
    @Test
    void mapsOnlyClosedCommandAndUsesTrustedStableOperationKey() throws Exception {
        var port = mock(MeetingToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        var command = new MeetingToolPort.CancelCommand(30, 2, "changed");
        when(port.cancel(context.actor(), command, "agent:10:20:meeting.cancel:v1"))
                .thenReturn(new MeetingToolPort.CancelResult(30, 8, "CANCELLED", 3, LocalDateTime.now()));
        var result = new MeetingCancelToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"bookingId\":30,\"version\":2,\"reason\":\"changed\"}"));
        assertThat(result.path("status").asText()).isEqualTo("CANCELLED");
        verify(port).cancel(context.actor(), command, "agent:10:20:meeting.cancel:v1");
        verifyNoMoreInteractions(port);
    }
}
