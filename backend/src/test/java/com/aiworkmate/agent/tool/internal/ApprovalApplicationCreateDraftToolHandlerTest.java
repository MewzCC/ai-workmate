package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalApplicationCreateDraftToolHandlerTest {
    private final ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");

    @Test
    void mapsClosedFieldsAndStableOperationKey() throws Exception {
        var command = new ApprovalApplicationToolPort.Draft("expense", "expense-flow", List.of(
                new ApprovalApplicationToolPort.FieldValue("reason", List.of("客户拜访"), false),
                new ApprovalApplicationToolPort.FieldValue("tags", List.of("差旅", "客户"), true)));
        when(port.createDraft(eq(context.actor()), eq(command),
                eq(new ToolOperationKey("agent:10:20:approval.application.createDraft:v1"))))
                .thenReturn(new ApprovalApplicationToolPort.WriteResult(30L, "expense", "DRAFT", 0));

        var output = new ApprovalApplicationCreateDraftToolHandler(port, mapper).execute(context,
                mapper.readTree("""
                        {"formKey":"expense","processKey":"expense-flow","fields":[
                          {"name":"reason","value":"客户拜访"},
                          {"name":"tags","values":["差旅","客户"]}
                        ]}
                        """));

        assertThat(output.path("applicationId").asLong()).isEqualTo(30L);
        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        verify(port).createDraft(context.actor(), command,
                new ToolOperationKey("agent:10:20:approval.application.createDraft:v1"));
    }

    @Test
    void rejectsAmbiguousFieldBeforeCallingPort() throws Exception {
        var handler = new ApprovalApplicationCreateDraftToolHandler(port, mapper);
        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("""
                {"formKey":"expense","fields":[{"name":"reason","value":"a","values":["b"]}]}
                """))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }
}
