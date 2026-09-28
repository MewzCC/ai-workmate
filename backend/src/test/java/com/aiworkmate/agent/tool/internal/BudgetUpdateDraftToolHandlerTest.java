package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BudgetUpdateDraftToolHandlerTest {
    @Test
    void forwardsOnlyMutableDraftFieldsAndOptimisticVersion() throws Exception {
        BudgetToolPort port = mock(BudgetToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 31L, 1, "trace");
        var command = new BudgetToolPort.BudgetDraftUpdate(
                81L, 0, "研发预算二期", 2027, 7L, new BigDecimal("120000.0"),
                "CNY", 85, "更新范围");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 1);
        when(port.updateBudgetDraft(context.actor(), command)).thenReturn(
                new BudgetToolPort.BudgetDraftResult(81L, "RD-2027", "DRAFT", 1, updatedAt));

        var output = new BudgetUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"budgetId":81,"version":0,"name":"研发预算二期","fiscalYear":2027,
                 "ownerUserId":7,"totalAmount":120000.00,"currency":"CNY",
                 "warningThreshold":85,"summary":"更新范围"}
                """));

        assertThat(output.path("budgetId").asLong()).isEqualTo(81L);
        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        assertThat(output.path("version").asInt()).isOne();
        verify(port).updateBudgetDraft(context.actor(), command);
    }

    @Test
    void rejectsMissingVersionBeforeCallingPort() throws Exception {
        BudgetToolPort port = mock(BudgetToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var handler = new BudgetUpdateDraftToolHandler(port, mapper);
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 31L, 1, "trace");

        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("""
                {"budgetId":81,"name":"研发预算二期","fiscalYear":2027,
                 "ownerUserId":7,"totalAmount":120000.00,"currency":"CNY","warningThreshold":85}
                """))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(port);
    }
}
