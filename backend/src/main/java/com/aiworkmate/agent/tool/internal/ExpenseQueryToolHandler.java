package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ExpenseQueryToolHandler extends TypedReadToolHandler<FinanceToolPort.ExpenseQuery, FinanceToolPort.Page<FinanceToolPort.Expense>> {
    private final FinanceToolPort port;
    public ExpenseQueryToolHandler(FinanceToolPort port, ObjectMapper mapper) { super(ToolCode.EXPENSE_QUERY, mapper); this.port = port; }
    @Override protected FinanceToolPort.ExpenseQuery parseArguments(JsonNode a) {
        return new FinanceToolPort.ExpenseQuery(optionalPositiveLong(a, "applicationId"), optionalText(a, "status"),
                pageNumber(a), pageSize(a));
    }
    @Override protected FinanceToolPort.Page<FinanceToolPort.Expense> invoke(TrustedToolContext c, FinanceToolPort.ExpenseQuery q) { return port.expenses(c.actor(), q); }
}
