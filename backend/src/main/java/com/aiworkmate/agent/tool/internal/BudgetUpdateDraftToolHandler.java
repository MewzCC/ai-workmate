package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetUpdateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.BudgetDraftUpdate, FinanceToolPort.BudgetDraftResult> {
    private final FinanceToolPort port;

    public BudgetUpdateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected FinanceToolPort.BudgetDraftUpdate parseArguments(JsonNode arguments) {
        return BudgetDraftArguments.parseUpdate(arguments);
    }

    @Override
    protected FinanceToolPort.BudgetDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.BudgetDraftUpdate command) {
        return port.updateBudgetDraft(context.actor(), command);
    }
}
