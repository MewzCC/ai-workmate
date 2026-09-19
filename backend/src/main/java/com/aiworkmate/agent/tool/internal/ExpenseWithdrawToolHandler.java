package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseWithdrawToolHandler
        extends TypedVersionedWriteToolHandler<ExpenseToolPort.ExpenseLifecycleResult> {
    private final ExpenseToolPort port;

    public ExpenseWithdrawToolHandler(ExpenseToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_WITHDRAW, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected ExpenseToolPort.ExpenseLifecycleResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.withdrawExpense(context.actor(), applicationId, version);
    }
}
