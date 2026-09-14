package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
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
        FinanceToolPort port = mock(FinanceToolPort.class);
        when(port.expenses(any(), any())).thenReturn(new FinanceToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.budgets(any(), any())).thenReturn(new FinanceToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.contracts(any(), any())).thenReturn(new FinanceToolPort.Page<>(List.of(), 0, 1, 20));
        when(port.suppliers(any(), any())).thenReturn(new FinanceToolPort.Page<>(List.of(), 0, 1, 20));

        var handlers = List.of(
                new ExpenseQueryToolHandler(port, mapper), new BudgetQueryToolHandler(port, mapper),
                new ContractQueryToolHandler(port, mapper), new SupplierQueryToolHandler(port, mapper));
        assertThat(handlers).extracting(ToolHandler::toolCode).containsExactly(
                ToolCode.EXPENSE_QUERY.code(), ToolCode.BUDGET_QUERY.code(),
                ToolCode.CONTRACT_QUERY.code(), ToolCode.SUPPLIER_QUERY.code());
        handlers.forEach(handler -> handler.execute(context, mapper.createObjectNode()));

        ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 5, "trace");
        verify(port).expenses(eq(actor), eq(new FinanceToolPort.ExpenseQuery(null, null, 1, 20)));
        verify(port).budgets(eq(actor), eq(new FinanceToolPort.BudgetQuery(null, null, null, null, 1, 20)));
        verify(port).contracts(eq(actor), eq(new FinanceToolPort.ContractQuery(null, null, null, null, null, 1, 20)));
        verify(port).suppliers(eq(actor), eq(new FinanceToolPort.SupplierQuery(null, null, null, null, 1, 20)));
    }
}
