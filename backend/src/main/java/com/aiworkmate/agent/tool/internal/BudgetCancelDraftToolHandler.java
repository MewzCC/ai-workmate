package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetCancelDraftToolHandler extends TypedVersionedWriteToolHandler<
        BudgetToolPort.BudgetDraftResult> {
    private final BudgetToolPort port;

    public BudgetCancelDraftToolHandler(BudgetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_CANCEL_DRAFT, objectMapper, "budgetId");
        this.port = port;
    }

    @Override
    protected BudgetToolPort.BudgetDraftResult invokeVersioned(
            TrustedToolContext context, long budgetId, int version) {
        return port.cancelBudgetDraft(context.actor(), budgetId, version);
    }
}
