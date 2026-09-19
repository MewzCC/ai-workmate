package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetUpdateDraftToolHandler extends TypedWriteToolHandler<
        BudgetToolPort.BudgetDraftUpdate, BudgetToolPort.BudgetDraftResult> {
    private final BudgetToolPort port;

    public BudgetUpdateDraftToolHandler(BudgetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected BudgetToolPort.BudgetDraftUpdate parseArguments(JsonNode arguments) {
        return BudgetDraftArguments.parseUpdate(arguments);
    }

    @Override
    protected BudgetToolPort.BudgetDraftResult invoke(
            TrustedToolContext context, BudgetToolPort.BudgetDraftUpdate command) {
        return port.updateBudgetDraft(context.actor(), command);
    }
}
