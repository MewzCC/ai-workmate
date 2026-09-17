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

class VisitorApplyToolHandlerTest {
    @Test
    void mapsClosedArgumentsAndUsesStableOperationKey() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 23L, 1, "trace");
        LocalDateTime visitAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        var command = new VisitorToolPort.ApplicationCommand(
                "访客甲", "合作公司", "13800000000", "项目交流", 9,
                visitAt, visitAt.plusHours(2), "粤A00000", 2);
        var operationKey = new ToolOperationKey("agent:10:23:visitor.apply:v1");
        when(port.apply(context.actor(), command, operationKey)).thenReturn(
                new VisitorToolPort.ApplicationResult(31, "PENDING", 0, visitAt.minusDays(1)));

        var output = new VisitorApplyToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"visitorName":"访客甲","visitorCompany":"合作公司","visitorPhone":"13800000000",
                 "purpose":"项目交流","hostUserId":9,"expectedVisitAt":"2026-09-20T09:00:00",
                 "expectedLeaveAt":"2026-09-20T11:00:00","plateNumber":"粤A00000","partySize":2}
                """));

        assertThat(output.path("bookingId").asLong()).isEqualTo(31);
        verify(port).apply(context.actor(), command, operationKey);
    }
}
