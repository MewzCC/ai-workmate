package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeChangeApplyToolHandlerTest {
    @Test
    void mapsClosedApplicationAndUsesStableOperationKey() throws Exception {
        EmployeeChangeToolPort port = mock(EmployeeChangeToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new EmployeeChangeToolPort.ApplicationCommand(
                31L, "TRANSFER", LocalDate.of(2026, 9, 30), 4L, 5L, null,
                12L, "团队调整");
        var operationKey = new ToolOperationKey("agent:10:25:hr.change.apply:v1");
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 18, 11, 30);
        when(port.apply(context.actor(), command, operationKey)).thenReturn(
                new EmployeeChangeToolPort.ApplicationResult(41L, "PENDING", 0, submittedAt));

        var output = new EmployeeChangeApplyToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"employeeUserId":31,"changeType":"TRANSFER","effectiveDate":"2026-09-30",
                         "targetDepartmentId":4,"targetPositionId":5,"reviewApproverUserId":12,
                         "reason":"团队调整"}
                        """));

        assertThat(output.path("changeId").asLong()).isEqualTo(41L);
        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        verify(port).apply(context.actor(), command, operationKey);
    }
}
