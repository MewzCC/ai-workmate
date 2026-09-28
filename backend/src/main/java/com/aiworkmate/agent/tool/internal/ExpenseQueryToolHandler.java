package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ExpenseQueryToolHandler extends TypedReadToolHandler<ExpenseToolPort.ExpenseQuery, ToolPage<ExpenseToolPort.Expense>> {
    private final ExpenseToolPort port;
    public ExpenseQueryToolHandler(ExpenseToolPort port, ObjectMapper mapper) { super(ToolCode.EXPENSE_QUERY, mapper); this.port = port; }
    @Override protected ExpenseToolPort.ExpenseQuery parseArguments(JsonNode a) {
        return new ExpenseToolPort.ExpenseQuery(optionalPositiveLong(a, "applicationId"), optionalText(a, "status"),
                pageNumber(a), pageSize(a));
    }
    @Override protected ToolPage<ExpenseToolPort.Expense> invoke(TrustedToolContext c, ExpenseToolPort.ExpenseQuery q) { return port.expenses(c.actor(), q); }
}
