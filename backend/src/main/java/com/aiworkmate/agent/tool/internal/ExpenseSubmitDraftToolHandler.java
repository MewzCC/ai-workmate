package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseSubmitDraftToolHandler
        extends TypedVersionedWriteToolHandler<ExpenseToolPort.ExpenseLifecycleResult> {
    private final ExpenseToolPort port;

    public ExpenseSubmitDraftToolHandler(ExpenseToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_SUBMIT_DRAFT, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected ExpenseToolPort.ExpenseLifecycleResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.submitExpenseDraft(context.actor(), applicationId, version);
    }
}
