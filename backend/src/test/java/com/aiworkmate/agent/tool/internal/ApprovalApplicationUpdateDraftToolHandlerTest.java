package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalApplicationUpdateDraftToolHandlerTest {
    @Test
    void forwardsClosedFieldsForOneVersionBoundDraft() throws Exception {
        ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var update = new ApprovalApplicationToolPort.DraftUpdate(null, List.of(
                new ApprovalApplicationToolPort.FieldValue("reason", List.of("客户拜访"), false)));
        when(port.updateDraft(context.actor(), 51, 4, update)).thenReturn(
                new ApprovalApplicationToolPort.WriteResult(51, "expense", "DRAFT", 5));

        var output = new ApprovalApplicationUpdateDraftToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"applicationId":51,"version":4,
                         "fields":[{"name":"reason","value":"客户拜访"}]}
                        """));

        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        verify(port).updateDraft(context.actor(), 51, 4, update);
    }
}
