package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseSubmitDraftToolHandler
        extends TypedVersionedWriteToolHandler<FinanceToolPort.ExpenseLifecycleResult> {
    private final FinanceToolPort port;

    public ExpenseSubmitDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_SUBMIT_DRAFT, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected FinanceToolPort.ExpenseLifecycleResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.submitExpenseDraft(context.actor(), applicationId, version);
    }
}
