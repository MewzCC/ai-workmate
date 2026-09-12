package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingQueryToolHandlerTest {
    @Test
    void usesTrustedActorCapsPageAndReturnsSafeFields() throws Exception {
        MeetingToolPort port = mock(MeetingToolPort.class);
        LocalDateTime from = LocalDateTime.of(2026, 9, 13, 9, 0);
        LocalDateTime to = from.plusDays(1);
        var context = new TrustedToolContext(1L, 7L, 1L, 1L, 1, "trace");
        var query = new MeetingToolPort.Query("一号", "OPEN", from, to, "BOOKED", 2, 50);
        when(port.query(context.actor(), query)).thenReturn(new MeetingToolPort.Result(
                List.of(new MeetingToolPort.Room(3L, "R-01", "一号会议室", "3F", 10,
                        "投影", "OPEN", null, false, false)),
                List.of(new MeetingToolPort.Booking(9L, 3L, "R-01", "一号会议室", "3F", "张三",
                        "周会", null, from, from.plusHours(1), 6, "BOOKED", 1,
                        null, null, null, from.minusDays(1), from.minusDays(1), true)), 1, 2, 50));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var output = new MeetingQueryToolHandler(port, mapper).execute(
                context, mapper.readTree(
                        "{\"keyword\":\"一号\",\"roomStatus\":\"OPEN\",\"from\":\"2026-09-13T09:00:00\",\"to\":\"2026-09-14T09:00:00\",\"bookingStatus\":\"BOOKED\",\"page\":2,\"size\":500}"));
        assertThat(output.at("/bookings/0/title").asText()).isEqualTo("周会");
        assertThat(output.toString()).doesNotContain("organizerUserId", "cancelledByUserId", "tenantId");
        verify(port).query(context.actor(), query);
    }
}
