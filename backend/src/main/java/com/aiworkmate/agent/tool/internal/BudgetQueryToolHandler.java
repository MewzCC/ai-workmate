package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class BudgetQueryToolHandler extends TypedReadToolHandler<FinanceToolPort.BudgetQuery, FinanceToolPort.Page<FinanceToolPort.Budget>> {
    private final FinanceToolPort port;
    public BudgetQueryToolHandler(FinanceToolPort port, ObjectMapper mapper) { super(ToolCode.BUDGET_QUERY, mapper); this.port = port; }
    @Override protected FinanceToolPort.BudgetQuery parseArguments(JsonNode a) {
        return new FinanceToolPort.BudgetQuery(optionalPositiveLong(a, "budgetId"), optionalText(a, "keyword"), optionalText(a, "status"),
                a.path("fiscalYear").isIntegralNumber() ? a.path("fiscalYear").intValue() : null,
                pageNumber(a), pageSize(a));
    }
    @Override protected FinanceToolPort.Page<FinanceToolPort.Budget> invoke(TrustedToolContext c, FinanceToolPort.BudgetQuery q) { return port.budgets(c.actor(), q); }
}
