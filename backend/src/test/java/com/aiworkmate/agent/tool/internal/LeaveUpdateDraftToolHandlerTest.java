package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveUpdateDraftToolHandlerTest {
    @Test
    void forwardsOneCompleteVersionBoundDraftWithoutSubmitting() throws Exception {
        LeaveToolPort port = mock(LeaveToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var draft = new LeaveToolPort.Draft("PERSONAL", 8L,
                LocalDate.of(2026, 10, 1), "AM", LocalDate.of(2026, 10, 1), "PM", "家庭事务");
        when(port.updateDraft(context.actor(), 41, 2, draft)).thenReturn(
                new LeaveToolPort.WriteResult(41, "DRAFT", 3, null));

        var output = new LeaveUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"applicationId":41,"version":2,"leaveType":"PERSONAL","approverUserId":8,
                 "startDate":"2026-10-01","startPeriod":"AM","endDate":"2026-10-01",
                 "endPeriod":"PM","reason":"家庭事务"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        assertThat(output.has("approvalTaskId")).isFalse();
        verify(port).updateDraft(context.actor(), 41, 2, draft);
    }
}
