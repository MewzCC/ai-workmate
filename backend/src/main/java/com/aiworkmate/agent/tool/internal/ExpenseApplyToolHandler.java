package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseApplyToolHandler
        extends TypedOperationKeyWriteToolHandler<ExpenseToolPort.ExpenseDraft, ExpenseToolPort.ExpenseLifecycleResult> {
    private final ExpenseToolPort port;
    public ExpenseApplyToolHandler(ExpenseToolPort port, ObjectMapper mapper) {
        super(ToolCode.EXPENSE_APPLY, mapper);
        this.port = port;
    }
    @Override protected ExpenseToolPort.ExpenseDraft parseArguments(JsonNode arguments) {
        return ExpenseDraftArguments.parse(arguments);
    }
    @Override protected ExpenseToolPort.ExpenseLifecycleResult invokeWithOperationKey(TrustedToolContext context,
            ExpenseToolPort.ExpenseDraft command, ToolOperationKey key) {
        return port.applyExpense(context.actor(), command, key);
    }
}
