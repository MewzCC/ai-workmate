package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseReopenToolHandlerTest {
    @Test
    void forwardsTrustedActorAndVersionedExpenseIdentity() throws Exception {
        ExpenseToolPort port = mock(ExpenseToolPort.class);
        var actor = new ToolActorContext(1, 7, 10, 20, 1, "trace");
        var result = new ExpenseToolPort.ExpenseLifecycleResult(
                51L, "expense-application", "DRAFT", 3);
        when(port.reopenExpense(actor, 51L, 2)).thenReturn(result);
        var handler = new ExpenseReopenToolHandler(port, new ObjectMapper());

        var output = handler.execute(new TrustedToolContext(1, 7, 10, 20, 1, "trace"),
                new ObjectMapper().readTree("{\"applicationId\":51,\"version\":2}"));

        assertThat(output.path("formKey").asText()).isEqualTo("expense-application");
        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        verify(port).reopenExpense(actor, 51L, 2);
    }
}
