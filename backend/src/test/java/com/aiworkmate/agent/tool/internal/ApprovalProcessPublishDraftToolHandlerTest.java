package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalProcessPublishDraftToolHandlerTest {
    @Test
    void publishesOnlyTheVersionBoundTrustedResource() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new ApprovalConfigurationToolPort.VersionedProcess(41L, 2);
        var expected = new ApprovalConfigurationToolPort.ProcessDraftResult(
                41L, "travel", "ENABLED", 3, LocalDateTime.of(2026, 10, 2, 0, 30));
        when(port.publishProcessDraft(context.actor(), command)).thenReturn(expected);

        var result = new ApprovalProcessPublishDraftToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"processId\":41,\"version\":2}"));

        assertThat(result.path("status").asText()).isEqualTo("ENABLED");
        verify(port).publishProcessDraft(context.actor(), command);
    }
}
