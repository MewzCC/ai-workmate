package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ApprovalTaskWriteToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");

    @Test
    void approveUsesOnlyTrustedActorAndBoundedCommand() throws Exception {
        var port = mock(ApprovalTaskToolPort.class);
        var command = new ApprovalTaskToolPort.DecisionCommand(30, 2, "同意");
        when(port.approve(context.actor(), command)).thenReturn(
                new ApprovalTaskToolPort.WriteResult(11, 30, "APPROVED", 4, "APPROVE"));

        var result = new ApprovalTaskApproveToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"taskId\":30,\"version\":2,\"comment\":\"同意\"}"));

        assertThat(result.path("action").asText()).isEqualTo("APPROVE");
        verify(port).approve(context.actor(), command);
        verifyNoMoreInteractions(port);
    }

    @Test
    void addSignKeepsTargetAsBusinessArgumentAndActorAsTrustedContext() throws Exception {
        var port = mock(ApprovalTaskToolPort.class);
        var command = new ApprovalTaskToolPort.AddSignCommand(30, 8, 2, "PRE", "专业复核");
        when(port.addSign(context.actor(), command)).thenReturn(
                new ApprovalTaskToolPort.WriteResult(11, 30, "PENDING", 4, "ADD_SIGN_PRE"));

        var result = new ApprovalTaskAddSignToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"taskId\":30,\"targetUserId\":8,\"version\":2,\"mode\":\"PRE\",\"reason\":\"专业复核\"}"));

        assertThat(result.path("action").asText()).isEqualTo("ADD_SIGN_PRE");
        verify(port).addSign(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
