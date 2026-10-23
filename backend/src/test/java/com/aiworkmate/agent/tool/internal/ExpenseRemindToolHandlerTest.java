package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExpenseRemindToolHandlerTest {
    @Test
    void usesTrustedIdentityAndReturnsOnlyTheActualVersionedReceipt() throws Exception {
        var mapper = new ObjectMapper(); var port = mock(ExpenseToolPort.class);
        var actor = new ToolActorContext(1, 7, 10, 20, 1, "trace");
        when(port.remindExpense(actor, 51, 2)).thenReturn(
                new ExpenseToolPort.ExpenseLifecycleResult(51, "expense-application", "PENDING", 3));
        var output = new ExpenseRemindToolHandler(port, mapper).execute(
                new TrustedToolContext(1, 7, 10, 20, 1, "trace"),
                mapper.readTree("{\"applicationId\":51,\"version\":2}"));
        assertThat(mapper.readTree(output.toString())).isEqualTo(mapper.readTree(
                "{\"applicationId\":51,\"formKey\":\"expense-application\",\"status\":\"PENDING\",\"version\":3}"));
        verify(port).remindExpense(actor, 51, 2);
    }

    @Test
    void rejectsMalformedVersionedIdentityWithoutCallingTheDomain() throws Exception {
        var mapper = new ObjectMapper(); var port = mock(ExpenseToolPort.class);
        var handler = new ExpenseRemindToolHandler(port, mapper);
        for (String invalid : new String[] {"{}", "{\"applicationId\":0,\"version\":2}",
                "{\"applicationId\":51,\"version\":-1}", "{\"applicationId\":51,\"version\":\"2\"}"}) {
            var args = mapper.readTree(invalid);
            assertThatThrownBy(() -> handler.execute(new TrustedToolContext(1, 7, 10, 20, 1, "trace"), args))
                    .isInstanceOf(RuntimeException.class);
        }
        verifyNoInteractions(port);
    }
}
