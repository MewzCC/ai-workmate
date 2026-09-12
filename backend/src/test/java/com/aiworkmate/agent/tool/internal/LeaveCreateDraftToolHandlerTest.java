package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveCreateDraftToolHandlerTest {
    @Mock
    private LeaveToolPort leaveToolPort;

    @Test
    void usesTrustedIdentityAndStableStepOperationKey() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveCreateDraftToolHandler handler = new LeaveCreateDraftToolHandler(
                leaveToolPort, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
        when(leaveToolPort.createDraft(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("agent:10:20:leave.createDraft:v1")))
                .thenReturn(application());

        var output = handler.execute(context, objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-01","startPeriod":"AM",
                 "endDate":"2026-09-01","endPeriod":"PM","reason":"家庭事务"}
                """));

        assertThat(output.toString()).isEqualTo("{\"applicationId\":30,\"status\":\"DRAFT\",\"version\":0}");
        ArgumentCaptor<LeaveToolPort.Draft> request =
                ArgumentCaptor.forClass(LeaveToolPort.Draft.class);
        verify(leaveToolPort).createDraft(
                org.mockito.ArgumentMatchers.eq(7L), request.capture(),
                org.mockito.ArgumentMatchers.eq("agent:10:20:leave.createDraft:v1"));
        assertThat(request.getValue().reason()).isEqualTo("家庭事务");
    }

    private LeaveToolPort.WriteResult application() {
        return new LeaveToolPort.WriteResult(30L, "DRAFT", 0, null);
    }
}
