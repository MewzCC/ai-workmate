package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApprovalFormCreateDraftToolHandlerTest {
    @Test
    void mapsBoundedFieldsAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new ApprovalConfigurationToolPort.FormDraft(
                "travel", "出差申请", null, List.of(new ApprovalConfigurationToolPort.FormField(
                "reason", "出差事由", "textarea", true, null, List.of(), "full")));
        var updatedAt = LocalDateTime.of(2026, 10, 1, 22, 10);
        when(port.createFormDraft(context.actor(), command)).thenReturn(
                new ApprovalConfigurationToolPort.FormDraftResult(31L, "travel", "DISABLED", 1, updatedAt));

        var result = new ApprovalFormCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"formKey":"travel","formName":"出差申请","fields":[
                  {"name":"reason","label":"出差事由","type":"textarea","required":true,"width":"full"}
                ]}
                """));

        assertThat(result.path("formId").asLong()).isEqualTo(31L);
        assertThat(result.path("status").asText()).isEqualTo("DISABLED");
        verify(port).createFormDraft(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
