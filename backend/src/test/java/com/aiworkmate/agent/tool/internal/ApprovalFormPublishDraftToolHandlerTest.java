package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalFormPublishDraftToolHandlerTest {
    @Test
    void publishesOnlyTheVersionBoundTrustedResource() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new ApprovalConfigurationToolPort.VersionedForm(31L, 2);
        var expected = new ApprovalConfigurationToolPort.FormDraftResult(
                31L, "travel", "ENABLED", 3, LocalDateTime.of(2026, 10, 2, 0, 10));
        when(port.publishFormDraft(context.actor(), command)).thenReturn(expected);

        var result = new ApprovalFormPublishDraftToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"formId\":31,\"version\":2}"));

        assertThat(result.path("status").asText()).isEqualTo("ENABLED");
        verify(port).publishFormDraft(context.actor(), command);
    }
}
