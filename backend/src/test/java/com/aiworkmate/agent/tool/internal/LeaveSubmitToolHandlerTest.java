package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveSubmitToolHandlerTest {

    @Test
    void submitsVersionBoundDraftUsingTrustedUserAndTaskEvidence() throws Exception {
        LeaveWorkflowService service = mock(LeaveWorkflowService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveSubmitToolHandler handler = new LeaveSubmitToolHandler(service, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 88L, 99L, 0, "trace");
        when(service.submitAgent(7L, 10L, new VersionRequest(3), 88L))
                .thenReturn(application());

        var output = handler.execute(context,
                objectMapper.readTree("{\"applicationId\":10,\"version\":3}"));

        assertThat(output.toString()).isEqualTo("{\"applicationId\":10,\"status\":\"PENDING\",\"version\":4}");
        verify(service).submitAgent(7L, 10L, new VersionRequest(3), 88L);
    }

    private LeaveApplicationResponse application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 20);
        return new LeaveApplicationResponse(
                10L, 7L, "当前用户", 8L, "主管", "PERSONAL",
                LocalDate.of(2026, 9, 1), "AM", LocalDate.of(2026, 9, 1), "PM",
                2, 1.0, "家庭事务", "PENDING", 4,
                30L, 0, "PENDING", now.plusDays(2), false, 0, null, null, true,
                "RUNNING", "APPROVAL", List.of(),
                now, null, now.minusDays(1), now, false, false, true, false, null, null);
    }
}
