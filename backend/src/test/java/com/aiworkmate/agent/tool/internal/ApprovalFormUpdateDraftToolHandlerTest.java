package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApprovalFormUpdateDraftToolHandlerTest {
    @Test
    void mapsVersionBoundFieldsAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new ApprovalConfigurationToolPort.FormDraftUpdate(
                31L, 2, "出差申请（新版）", null, List.of(new ApprovalConfigurationToolPort.FormField(
                "reason", "出差事由", "textarea", true, null, List.of(), "full")));
        var updatedAt = LocalDateTime.of(2026, 10, 1, 22, 30);
        when(port.updateFormDraft(context.actor(), command)).thenReturn(
                new ApprovalConfigurationToolPort.FormDraftResult(31L, "travel", "DISABLED", 3, updatedAt));

        var result = new ApprovalFormUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"formId":31,"version":2,"formName":"出差申请（新版）","fields":[
                  {"name":"reason","label":"出差事由","type":"textarea","required":true,"width":"full"}
                ]}
                """));

        assertThat(result.path("formId").asLong()).isEqualTo(31L);
        assertThat(result.path("version").asInt()).isEqualTo(3);
        verify(port).updateFormDraft(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
