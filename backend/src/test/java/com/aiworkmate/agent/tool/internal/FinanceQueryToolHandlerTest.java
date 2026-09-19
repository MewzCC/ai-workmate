package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FinanceQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(1L, 2L, 3L, 4L, 5, "trace");

    @Test
    void dispatchesFourNarrowToolsWithGatewayDerivedActor() throws Exception {
        ExpenseToolPort expensePort = mock(ExpenseToolPort.class);
        BudgetToolPort budgetPort = mock(BudgetToolPort.class);
        ContractToolPort contractPort = mock(ContractToolPort.class);
        SupplierToolPort supplierPort = mock(SupplierToolPort.class);
        when(expensePort.expenses(any(), any())).thenReturn(new ToolPage<>(List.of(), 0, 1, 20));
        when(budgetPort.budgets(any(), any())).thenReturn(new ToolPage<>(List.of(), 0, 1, 20));
        when(contractPort.contracts(any(), any())).thenReturn(new ToolPage<>(List.of(), 0, 1, 20));
        when(supplierPort.suppliers(any(), any())).thenReturn(new ToolPage<>(List.of(), 0, 1, 20));

        var handlers = List.of(
                new ExpenseQueryToolHandler(expensePort, mapper), new BudgetQueryToolHandler(budgetPort, mapper),
                new ContractQueryToolHandler(contractPort, mapper), new SupplierQueryToolHandler(supplierPort, mapper));
        assertThat(handlers).extracting(ToolHandler::toolCode).containsExactly(
                ToolCode.EXPENSE_QUERY.code(), ToolCode.BUDGET_QUERY.code(),
                ToolCode.CONTRACT_QUERY.code(), ToolCode.SUPPLIER_QUERY.code());
        handlers.forEach(handler -> handler.execute(context, mapper.createObjectNode()));

        ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 5, "trace");
        verify(expensePort).expenses(eq(actor), eq(new ExpenseToolPort.ExpenseQuery(null, null, 1, 20)));
        verify(budgetPort).budgets(eq(actor), eq(new BudgetToolPort.BudgetQuery(null, null, null, null, 1, 20)));
        verify(contractPort).contracts(eq(actor), eq(new ContractToolPort.ContractQuery(null, null, null, null, null, 1, 20)));
        verify(supplierPort).suppliers(eq(actor), eq(new SupplierToolPort.SupplierQuery(null, null, null, null, 1, 20)));
    }
}
