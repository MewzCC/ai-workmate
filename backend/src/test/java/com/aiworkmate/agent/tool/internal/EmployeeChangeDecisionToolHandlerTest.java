package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeChangeDecisionToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 10L, 20L, 1, "trace");

    @Test
    void approvesOneVersionMatchedAssignedChange() throws Exception {
        EmployeeChangeToolPort port = mock(EmployeeChangeToolPort.class);
        var command = new EmployeeChangeToolPort.DecisionCommand(41L, 0, "同意");
        when(port.approve(context.actor(), command)).thenReturn(
                new EmployeeChangeToolPort.ChangeActionResult(41L, "EFFECTIVE", 1));

        var output = new EmployeeChangeApproveToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"changeId\":41,\"expectedVersion\":0,\"comment\":\"同意\"}"));

        assertThat(output.path("status").asText()).isEqualTo("EFFECTIVE");
        verify(port).approve(context.actor(), command);
    }

    @Test
    void rejectsOneAssignedChangeOnlyWithReason() throws Exception {
        EmployeeChangeToolPort port = mock(EmployeeChangeToolPort.class);
        var handler = new EmployeeChangeRejectToolHandler(port, mapper);
        assertThatThrownBy(() -> handler.execute(context,
                mapper.readTree("{\"changeId\":41,\"expectedVersion\":0}")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        var command = new EmployeeChangeToolPort.DecisionCommand(41L, 0, "资料不完整");
        when(port.reject(context.actor(), command)).thenReturn(
                new EmployeeChangeToolPort.ChangeActionResult(41L, "REJECTED", 1));
        handler.execute(context, mapper.readTree(
                "{\"changeId\":41,\"expectedVersion\":0,\"comment\":\"资料不完整\"}"));
        verify(port).reject(context.actor(), command);
    }

    @Test
    void withdrawsOnlyOneVersionMatchedOwnedChange() throws Exception {
        EmployeeChangeToolPort port = mock(EmployeeChangeToolPort.class);
        var command = new EmployeeChangeToolPort.VersionedCommand(41L, 0);
        when(port.withdraw(context.actor(), command)).thenReturn(
                new EmployeeChangeToolPort.ChangeActionResult(41L, "WITHDRAWN", 1));

        var output = new EmployeeChangeWithdrawToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"changeId\":41,\"expectedVersion\":0}"));

        assertThat(output.path("version").asInt()).isEqualTo(1);
        verify(port).withdraw(context.actor(), command);
    }
}
