package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceSettingsUpdateToolHandlerTest {
    @Test
    void updatesOnlyTheCurrentTenantThroughTheGatewayActor() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new AttendanceToolPort.SettingsUpdateCommand(
                2, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true);
        var result = new AttendanceToolPort.SettingsUpdateResult(
                3, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true,
                LocalDateTime.of(2026, 10, 1, 18, 10));
        when(port.updateSettings(context.actor(), command)).thenReturn(result);

        var output = new AttendanceSettingsUpdateToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"version":2,"workStartTime":"08:30","workEndTime":"17:30","startFlexMinutes":20,"endFlexMinutes":10,"flexLinked":true}
                """));

        assertThat(output.path("version").asInt()).isEqualTo(3);
        verify(port).updateSettings(context.actor(), command);
    }

    @Test
    void rejectsInvalidWorkHoursBeforeCallingTheDomain() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper();

        assertThatThrownBy(() -> new AttendanceSettingsUpdateToolHandler(port, mapper).execute(
                new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace"), mapper.readTree("""
                        {"version":2,"workStartTime":"18:00","workEndTime":"09:00","startFlexMinutes":20,"endFlexMinutes":10,"flexLinked":true}
                        """))).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
