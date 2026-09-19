package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class BudgetQueryToolHandler extends TypedReadToolHandler<BudgetToolPort.BudgetQuery, ToolPage<BudgetToolPort.Budget>> {
    private final BudgetToolPort port;
    public BudgetQueryToolHandler(BudgetToolPort port, ObjectMapper mapper) { super(ToolCode.BUDGET_QUERY, mapper); this.port = port; }
    @Override protected BudgetToolPort.BudgetQuery parseArguments(JsonNode a) {
        return new BudgetToolPort.BudgetQuery(optionalPositiveLong(a, "budgetId"), optionalText(a, "keyword"), optionalText(a, "status"),
                a.path("fiscalYear").isIntegralNumber() ? a.path("fiscalYear").intValue() : null,
                pageNumber(a), pageSize(a));
    }
    @Override protected ToolPage<BudgetToolPort.Budget> invoke(TrustedToolContext c, BudgetToolPort.BudgetQuery q) { return port.budgets(c.actor(), q); }
}
