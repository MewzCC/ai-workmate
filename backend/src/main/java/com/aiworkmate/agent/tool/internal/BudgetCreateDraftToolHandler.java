package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class BudgetCreateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.BudgetDraft, FinanceToolPort.BudgetDraftResult> {
    private final FinanceToolPort port;

    public BudgetCreateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.BUDGET_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected FinanceToolPort.BudgetDraft parseArguments(JsonNode arguments) {
        return BudgetDraftArguments.parse(arguments);
    }

    @Override
    protected FinanceToolPort.BudgetDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.BudgetDraft command) {
        return port.createBudgetDraft(context.actor(), command);
    }
}
