package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetActivateDraftToolHandlerTest {
    @Test
    void forwardsOnlyBudgetIdAndVersionToFixedTransition() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 32L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 2);
        when(port.activateBudgetDraft(context.actor(), 81L, 1)).thenReturn(
                new FinanceToolPort.BudgetDraftResult(81L, "RD-2027", "ACTIVE", 2, updatedAt));

        var output = new BudgetActivateDraftToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"budgetId\":81,\"version\":1}"));

        assertThat(output.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(output.path("version").asInt()).isEqualTo(2);
        verify(port).activateBudgetDraft(context.actor(), 81L, 1);
    }
}
