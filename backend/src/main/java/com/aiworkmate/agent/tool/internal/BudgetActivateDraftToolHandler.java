package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetActivateDraftToolHandler extends TypedVersionedWriteToolHandler<
        BudgetToolPort.BudgetDraftResult> {
    private final BudgetToolPort port;

    public BudgetActivateDraftToolHandler(BudgetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_ACTIVATE_DRAFT, objectMapper, "budgetId");
        this.port = port;
    }

    @Override
    protected BudgetToolPort.BudgetDraftResult invokeVersioned(
            TrustedToolContext context, long budgetId, int version) {
        return port.activateBudgetDraft(context.actor(), budgetId, version);
    }
}
