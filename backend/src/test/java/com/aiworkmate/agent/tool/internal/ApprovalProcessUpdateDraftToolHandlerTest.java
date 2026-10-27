package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalProcessUpdateDraftToolHandlerTest {
    @Test
    void mapsVersionBoundSemanticNodesAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        when(port.updateProcessDraft(eq(context.actor()), any())).thenReturn(
                new ApprovalConfigurationToolPort.ProcessDraftResult(
                        41L, "travel", "DISABLED", 3, LocalDateTime.of(2026, 10, 1, 23, 10)));

        var result = new ApprovalProcessUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"processId":41,"version":2,"processName":"出差审批（新版）","nodes":[
                  {"nodeType":"START","nodeName":"开始"},
                  {"nodeType":"APPROVAL","nodeName":"主管审批","approveType":"DIRECT_MANAGER","mode":"OR_SIGN"},
                  {"nodeType":"END","nodeName":"结束"}]}
                """));

        assertThat(result.path("version").asInt()).isEqualTo(3);
        var command = org.mockito.ArgumentCaptor.forClass(ApprovalConfigurationToolPort.ProcessDraftUpdate.class);
        verify(port).updateProcessDraft(eq(context.actor()), command.capture());
        assertThat(command.getValue().processId()).isEqualTo(41L);
        assertThat(command.getValue().version()).isEqualTo(2);
        assertThat(command.getValue().nodes()).hasSize(3);
    }
}
