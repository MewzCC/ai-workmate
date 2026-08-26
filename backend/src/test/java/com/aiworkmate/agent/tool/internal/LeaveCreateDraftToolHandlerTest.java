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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveCreateDraftToolHandlerTest {
    @Mock
    private LeaveWorkflowService leaveWorkflowService;

    @Test
    void usesTrustedIdentityAndStableStepOperationKey() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveCreateDraftToolHandler handler = new LeaveCreateDraftToolHandler(
                leaveWorkflowService, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        when(leaveWorkflowService.createAgentDraft(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("agent:10:20:leave.createDraft:v1")))
                .thenReturn(application());

        var output = handler.execute(context, objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-01","startPeriod":"AM",
                 "endDate":"2026-09-01","endPeriod":"PM","reason":"家庭事务"}
                """));

        assertThat(output.toString()).isEqualTo("{\"applicationId\":30,\"status\":\"DRAFT\",\"version\":0}");
        ArgumentCaptor<com.aiworkmate.dto.LeaveApplicationRequest> request =
                ArgumentCaptor.forClass(com.aiworkmate.dto.LeaveApplicationRequest.class);
        verify(leaveWorkflowService).createAgentDraft(
                org.mockito.ArgumentMatchers.eq(7L), request.capture(),
                org.mockito.ArgumentMatchers.eq("agent:10:20:leave.createDraft:v1"));
        assertThat(request.getValue().reason()).isEqualTo("家庭事务");
        assertThat(request.getValue().version()).isNull();
    }

    private LeaveApplicationResponse application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        return new LeaveApplicationResponse(
                30L, 7L, "当前用户", null, null, "PERSONAL",
                LocalDate.of(2026, 9, 1), "AM", LocalDate.of(2026, 9, 1), "PM",
                2, 1.0, "家庭事务", "DRAFT", 0,
                null, null, null, null, false, null, "DRAFT", List.of(),
                null, null, now, now, true, true, false, false, null, null);
    }
}
