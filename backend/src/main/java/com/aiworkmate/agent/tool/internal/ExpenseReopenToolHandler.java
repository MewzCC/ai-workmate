package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseReopenToolHandler
        extends TypedVersionedWriteToolHandler<FinanceToolPort.ExpenseLifecycleResult> {
    private final FinanceToolPort port;

    public ExpenseReopenToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_REOPEN, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected FinanceToolPort.ExpenseLifecycleResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.reopenExpense(context.actor(), applicationId, version);
    }
}
