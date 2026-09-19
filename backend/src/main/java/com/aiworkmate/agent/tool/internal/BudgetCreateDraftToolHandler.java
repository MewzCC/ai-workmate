package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetCreateDraftToolHandler extends TypedWriteToolHandler<
        BudgetToolPort.BudgetDraft, BudgetToolPort.BudgetDraftResult> {
    private final BudgetToolPort port;

    public BudgetCreateDraftToolHandler(BudgetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected BudgetToolPort.BudgetDraft parseArguments(JsonNode arguments) {
        return BudgetDraftArguments.parse(arguments);
    }

    @Override
    protected BudgetToolPort.BudgetDraftResult invoke(
            TrustedToolContext context, BudgetToolPort.BudgetDraft command) {
        return port.createBudgetDraft(context.actor(), command);
    }
}
