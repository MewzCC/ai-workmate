package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceReissueDecideToolHandlerTest {
    @Test
    void decidesOnlyTheVersionedRequestAssignedByTheGateway() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new AttendanceToolPort.ReissueDecisionCommand(
                31L, 2, "APPROVED", "同意");
        var result = new AttendanceToolPort.ReissueDecisionResult(
                31L, "APPROVED", 3, LocalDateTime.of(2026, 10, 1, 9, 0));
        when(port.decideReissue(context.actor(), command)).thenReturn(result);

        var output = new AttendanceReissueDecideToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"reissueId\":31,\"version\":2,\"decision\":\"APPROVED\",\"comment\":\"同意\"}"));

        assertThat(output.path("status").asText()).isEqualTo("APPROVED");
        assertThat(output.path("version").asInt()).isEqualTo(3);
        verify(port).decideReissue(context.actor(), command);
    }

    @Test
    void rejectsRejectionWithoutReasonBeforeCallingTheDomain() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var handler = new AttendanceReissueDecideToolHandler(port, mapper);

        assertThatThrownBy(() -> handler.execute(
                new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace"),
                mapper.readTree("{\"reissueId\":31,\"version\":2,\"decision\":\"REJECTED\"}")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
