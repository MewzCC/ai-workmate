package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingBookToolHandlerTest {
    @Mock private MeetingToolPort port;

    @Test
    void usesGatewayIdentityAndStableOperationKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        var result = new MeetingToolPort.WriteResult(30L, 8L, "BOOKED", 0,
                LocalDateTime.parse("2026-09-20T10:00:00"), LocalDateTime.parse("2026-09-20T11:00:00"));
        when(port.book(eq(context.actor()), any(),
                eq(new ToolOperationKey("agent:10:20:meeting.book:v1")))).thenReturn(result);

        var output = new MeetingBookToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"roomId":8,"title":"产品评审","agenda":"确认发布范围",
                 "startAt":"2026-09-20T10:00:00","endAt":"2026-09-20T11:00:00","attendeeCount":6}
                """));

        assertThat(output.path("bookingId").asLong()).isEqualTo(30L);
        assertThat(output.path("status").asText()).isEqualTo("BOOKED");
        ArgumentCaptor<MeetingToolPort.BookCommand> command = ArgumentCaptor.forClass(MeetingToolPort.BookCommand.class);
        verify(port).book(eq(context.actor()), command.capture(),
                eq(new ToolOperationKey("agent:10:20:meeting.book:v1")));
        assertThat(command.getValue().title()).isEqualTo("产品评审");
        assertThat(command.getValue().attendeeCount()).isEqualTo(6);
    }
}
