package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExpenseCancelDraftToolHandlerTest {
    @Test
    void forwardsOnlyTrustedActorAndVersionedIdentityAndReturnsTheActualReceipt() throws Exception {
        var mapper = new ObjectMapper(); var port = mock(ExpenseToolPort.class);
        var actor = new ToolActorContext(1, 7, 10, 20, 1, "trace");
        when(port.cancelExpenseDraft(actor, 51, 0)).thenReturn(
                new ExpenseToolPort.ExpenseLifecycleResult(51, "expense-application", "CANCELLED", 1));
        var handler = new ExpenseCancelDraftToolHandler(port, mapper);
        var output = handler.execute(new TrustedToolContext(1, 7, 10, 20, 1, "trace"),
                mapper.readTree("{\"applicationId\":51,\"version\":0}"));
        assertThat(mapper.readTree(output.toString())).isEqualTo(mapper.readTree(
                "{\"applicationId\":51,\"formKey\":\"expense-application\",\"status\":\"CANCELLED\",\"version\":1}"));
        verify(port).cancelExpenseDraft(actor, 51, 0);
    }

    @Test
    void rejectsMalformedIdentityAndVersionBeforeInvokingThePort() throws Exception {
        var mapper = new ObjectMapper(); var port = mock(ExpenseToolPort.class);
        var handler = new ExpenseCancelDraftToolHandler(port, mapper);
        for (String invalid : new String[] {"{}", "{\"applicationId\":0,\"version\":0}",
                "{\"applicationId\":51,\"version\":-1}", "{\"applicationId\":\"51\",\"version\":0}"}) {
            var args = mapper.readTree(invalid);
            assertThatThrownBy(() -> handler.execute(new TrustedToolContext(1, 7, 10, 20, 1, "trace"), args))
                    .isInstanceOf(RuntimeException.class);
        }
        verifyNoInteractions(port);
    }
}
