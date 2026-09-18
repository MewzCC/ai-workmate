package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseCreateDraftToolHandlerTest {
    @Test
    void mapsOnlyExpenseFieldsAndUsesStableOperationKey() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 26L, 1, "trace");
        var command = new FinanceToolPort.ExpenseDraft(
                new BigDecimal("88.5"), "TRAVEL", LocalDate.of(2026, 9, 17),
                "INV-1", "客户拜访");
        var operationKey = new ToolOperationKey("agent:10:26:expense.createDraft:v1");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 18, 12, 0);
        when(port.createExpenseDraft(context.actor(), command, operationKey)).thenReturn(
                new FinanceToolPort.ExpenseDraftResult(
                        51L, "expense-application", "DRAFT", 0, createdAt));

        var output = new ExpenseCreateDraftToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"amount":88.50,"category":"TRAVEL","expenseDate":"2026-09-17",
                         "invoiceNumber":"INV-1","reason":"客户拜访"}
                        """));

        assertThat(output.path("applicationId").asLong()).isEqualTo(51L);
        assertThat(output.path("formKey").asText()).isEqualTo("expense-application");
        verify(port).createExpenseDraft(context.actor(), command, operationKey);
    }
}
