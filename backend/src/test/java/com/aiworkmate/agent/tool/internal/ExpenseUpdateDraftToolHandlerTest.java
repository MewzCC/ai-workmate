package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseUpdateDraftToolHandlerTest {
    @Test
    void forwardsOnlyTheBoundedPatchAndTrustedActor() throws Exception {
        ExpenseToolPort port = mock(ExpenseToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 27L, 1, "trace");
        var patch = new ExpenseToolPort.ExpenseDraft(
                new BigDecimal("99.5"), null, null, null, "调整差旅金额");
        when(port.updateExpenseDraft(context.actor(), 51L, 0, patch)).thenReturn(
                new ExpenseToolPort.ExpenseLifecycleResult(
                        51L, "expense-application", "DRAFT", 1));

        var output = new ExpenseUpdateDraftToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"applicationId":51,"version":0,
                         "amount":99.50,"reason":"调整差旅金额"}
                        """));

        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        assertThat(output.path("version").asInt()).isOne();
        verify(port).updateExpenseDraft(context.actor(), 51L, 0, patch);
    }
}
