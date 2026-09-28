package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseReopenToolHandler
        extends TypedVersionedWriteToolHandler<ExpenseToolPort.ExpenseLifecycleResult> {
    private final ExpenseToolPort port;

    public ExpenseReopenToolHandler(ExpenseToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_REOPEN, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected ExpenseToolPort.ExpenseLifecycleResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.reopenExpense(context.actor(), applicationId, version);
    }
}
