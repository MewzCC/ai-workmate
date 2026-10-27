package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApprovalProcessCreateDraftToolHandlerTest {
    @Test
    void mapsSemanticNodesAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        when(port.createProcessDraft(eq(context.actor()), any())).thenReturn(
                new ApprovalConfigurationToolPort.ProcessDraftResult(
                        41L, "travel", "DISABLED", 1, LocalDateTime.of(2026, 10, 1, 22, 50)));

        var result = new ApprovalProcessCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"processKey":"travel","processName":"出差审批","nodes":[
                  {"nodeType":"START","nodeName":"开始"},
                  {"nodeType":"APPROVAL","nodeName":"主管审批","approveType":"DIRECT_MANAGER","mode":"OR_SIGN"},
                  {"nodeType":"END","nodeName":"结束"}]}
                """));

        assertThat(result.path("status").asText()).isEqualTo("DISABLED");
        var command = org.mockito.ArgumentCaptor.forClass(ApprovalConfigurationToolPort.ProcessDraft.class);
        verify(port).createProcessDraft(eq(context.actor()), command.capture());
        assertThat(command.getValue().nodes()).hasSize(3);
    }
}
