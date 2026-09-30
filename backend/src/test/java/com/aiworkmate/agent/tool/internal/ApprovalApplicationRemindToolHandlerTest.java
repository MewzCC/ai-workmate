package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalApplicationRemindToolHandlerTest {
    @Test
    void forwardsOnlyOneVersionBoundOwnedApplication() throws Exception {
        ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        when(port.remind(context.actor(), 51, 4)).thenReturn(
                new ApprovalApplicationToolPort.WriteResult(51, "expense", "PENDING", 5));

        var output = new ApprovalApplicationRemindToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"applicationId":51,"version":4}
                """));

        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        verify(port).remind(context.actor(), 51, 4);
    }
}
