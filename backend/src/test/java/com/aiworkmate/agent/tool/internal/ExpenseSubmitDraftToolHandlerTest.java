package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseSubmitDraftToolHandlerTest {
    @Test
    void forwardsTrustedActorAndVersionedExpenseIdentity() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        var result = new FinanceToolPort.ExpenseLifecycleResult(
                51L, "expense-application", "PENDING", 1);
        when(port.submitExpenseDraft(
                new ToolActorContext(1, 7, 10, 20, 1, "trace"), 51L, 0))
                .thenReturn(result);
        var handler = new ExpenseSubmitDraftToolHandler(port, new ObjectMapper());

        var output = handler.execute(new TrustedToolContext(1, 7, 10, 20, 1, "trace"),
                new ObjectMapper().readTree("{\"applicationId\":51,\"version\":0}"));

        assertThat(output.path("formKey").asText()).isEqualTo("expense-application");
        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        verify(port).submitExpenseDraft(
                new ToolActorContext(1, 7, 10, 20, 1, "trace"), 51L, 0);
    }
}
