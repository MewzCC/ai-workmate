package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReissueApplyToolHandlerTest {
    @Mock private AttendanceToolPort port;

    @Test
    void usesGatewayIdentityTypedCommandAndStableOperationKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        LocalDate date = LocalDate.of(2026, 9, 14);
        var result = new AttendanceToolPort.ReissueWriteResult(
                30L, "PENDING", date, "CLOCK_IN", LocalDateTime.of(2026, 9, 15, 9, 0));
        when(port.submitReissue(eq(context.actor()), any(),
                eq(new ToolOperationKey("agent:10:20:attendance.reissue.apply:v1")))).thenReturn(result);

        var output = new AttendanceReissueApplyToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"clockDate":"2026-09-14","clockType":"CLOCK_IN","reason":"忘记打卡"}
                """));

        assertThat(output.path("reissueId").asLong()).isEqualTo(30L);
        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        var command = ArgumentCaptor.forClass(AttendanceToolPort.ReissueCommand.class);
        verify(port).submitReissue(eq(context.actor()), command.capture(),
                eq(new ToolOperationKey("agent:10:20:attendance.reissue.apply:v1")));
        assertThat(command.getValue()).isEqualTo(
                new AttendanceToolPort.ReissueCommand(date, "CLOCK_IN", "忘记打卡"));
    }

    @Test
    void rejectsInvalidClockTypeBeforeCallingDomainPort() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var handler = new AttendanceReissueApplyToolHandler(port, mapper);
        assertThatThrownBy(() -> handler.execute(
                new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace"), mapper.readTree("""
                        {"clockDate":"2026-09-14","clockType":"OTHER","reason":"忘记打卡"}
                        """))).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verifyNoInteractions(port);
    }
}
