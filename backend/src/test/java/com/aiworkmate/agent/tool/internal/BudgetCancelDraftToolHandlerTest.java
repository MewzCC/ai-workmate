package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetCancelDraftToolHandlerTest {
    @Test
    void forwardsOnlyBudgetIdAndVersionToFixedTransition() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 33L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 3);
        when(port.cancelBudgetDraft(context.actor(), 82L, 0)).thenReturn(
                new FinanceToolPort.BudgetDraftResult(82L, "OPS-2027", "CANCELLED", 1, updatedAt));

        var output = new BudgetCancelDraftToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"budgetId\":82,\"version\":0}"));

        assertThat(output.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(output.path("version").asInt()).isOne();
        verify(port).cancelBudgetDraft(context.actor(), 82L, 0);
    }
}
