package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveApplyToolHandlerTest {
    @Mock
    private LeaveWorkflowService leaveWorkflowService;

    @Test
    void usesOnlyGatewayIdentityAndReturnsSubmittedApplication() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveApplyToolHandler handler = new LeaveApplyToolHandler(leaveWorkflowService, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        when(leaveWorkflowService.applyAgent(eq(7L), any(),
                eq("agent:10:20:leave.apply:v1"))).thenReturn(application());

        var output = handler.execute(context, objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-09","startPeriod":"AM",
                 "endDate":"2026-09-09","endPeriod":"PM","reason":"家庭事务"}
                """));

        assertThat(output.toString()).isEqualTo(
                "{\"applicationId\":30,\"status\":\"PENDING\",\"version\":1,\"approvalTaskId\":40}");
        ArgumentCaptor<com.aiworkmate.dto.LeaveApplicationRequest> request =
                ArgumentCaptor.forClass(com.aiworkmate.dto.LeaveApplicationRequest.class);
        verify(leaveWorkflowService).applyAgent(eq(7L), request.capture(),
                eq("agent:10:20:leave.apply:v1"));
        assertThat(request.getValue().reason()).isEqualTo("家庭事务");
        assertThat(request.getValue().version()).isNull();
    }

    private LeaveApplicationResponse application() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 15, 0);
        return new LeaveApplicationResponse(
                30L, 7L, "当前用户", 8L, "直属主管", "PERSONAL",
                LocalDate.of(2026, 9, 9), "AM", LocalDate.of(2026, 9, 9), "PM",
                2, 1.0, "家庭事务", "PENDING", 1,
                40L, 0, "PENDING", now.plusHours(48), false, 0, null, null, false,
                "RUNNING", "APPROVAL", List.of(), now, null, now, now,
                false, false, true, false, null, null);
    }
}
