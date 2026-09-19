package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetCancelDraftToolHandler extends TypedVersionedWriteToolHandler<
        FinanceToolPort.BudgetDraftResult> {
    private final FinanceToolPort port;

    public BudgetCancelDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_CANCEL_DRAFT, objectMapper, "budgetId");
        this.port = port;
    }

    @Override
    protected FinanceToolPort.BudgetDraftResult invokeVersioned(
            TrustedToolContext context, long budgetId, int version) {
        return port.cancelBudgetDraft(context.actor(), budgetId, version);
    }
}
