package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ExpenseToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseCreateDraftToolHandler extends TypedOperationKeyWriteToolHandler<
        ExpenseToolPort.ExpenseDraft, ExpenseToolPort.ExpenseDraftResult> {
    private final ExpenseToolPort port;

    public ExpenseCreateDraftToolHandler(ExpenseToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ExpenseToolPort.ExpenseDraft parseArguments(JsonNode arguments) {
        return ExpenseDraftArguments.parse(arguments);
    }

    @Override
    protected ExpenseToolPort.ExpenseDraftResult invokeWithOperationKey(
            TrustedToolContext context, ExpenseToolPort.ExpenseDraft command,
            ToolOperationKey operationKey) {
        return port.createExpenseDraft(context.actor(), command, operationKey);
    }
}
