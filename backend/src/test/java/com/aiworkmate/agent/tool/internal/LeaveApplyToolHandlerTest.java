package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveApplyToolHandlerTest {
    @Mock
    private LeaveToolPort leaveToolPort;

    @Test
    void usesOnlyGatewayIdentityAndReturnsSubmittedApplication() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveApplyToolHandler handler = new LeaveApplyToolHandler(leaveToolPort, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        when(leaveToolPort.apply(eq(context.actor()), any(),
                eq(new ToolOperationKey("agent:10:20:leave.apply:v1")))).thenReturn(application());

        var output = handler.execute(context, objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-09","startPeriod":"AM",
                 "endDate":"2026-09-09","endPeriod":"PM","reason":"家庭事务"}
                """));

        assertThat(output.toString()).isEqualTo(
                "{\"applicationId\":30,\"status\":\"PENDING\",\"version\":1,\"approvalTaskId\":40}");
        ArgumentCaptor<LeaveToolPort.Draft> request =
                ArgumentCaptor.forClass(LeaveToolPort.Draft.class);
        verify(leaveToolPort).apply(eq(context.actor()), request.capture(),
                eq(new ToolOperationKey("agent:10:20:leave.apply:v1")));
        assertThat(request.getValue().reason()).isEqualTo("家庭事务");
    }

    private LeaveToolPort.WriteResult application() {
        return new LeaveToolPort.WriteResult(30L, "PENDING", 1, 40L);
    }
}
