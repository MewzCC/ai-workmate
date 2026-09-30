package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceClockToolHandlerTest {
    @Test
    void clocksOnlyTheAuthenticatedActorAtServerTime() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var clockIn = LocalDateTime.of(2026, 9, 30, 9, 0);
        when(port.clock(context.actor(), "CLOCK_IN")).thenReturn(
                new AttendanceToolPort.ClockWriteResult(81L, LocalDate.of(2026, 9, 30),
                        clockIn, null, "NORMAL", 0, 0));

        var output = new AttendanceClockToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"clockType":"CLOCK_IN"}
                        """));

        assertThat(output.path("recordId").asLong()).isEqualTo(81L);
        assertThat(output.path("status").asText()).isEqualTo("NORMAL");
        verify(port).clock(context.actor(), "CLOCK_IN");
    }
}
