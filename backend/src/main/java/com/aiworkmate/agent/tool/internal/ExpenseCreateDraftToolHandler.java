package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ExpenseCreateDraftToolHandler extends TypedOperationKeyWriteToolHandler<
        FinanceToolPort.ExpenseDraft, FinanceToolPort.ExpenseDraftResult> {
    private final FinanceToolPort port;

    public ExpenseCreateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected FinanceToolPort.ExpenseDraft parseArguments(JsonNode arguments) {
        return ExpenseDraftArguments.parse(arguments);
    }

    @Override
    protected FinanceToolPort.ExpenseDraftResult invokeWithOperationKey(
            TrustedToolContext context, FinanceToolPort.ExpenseDraft command,
            ToolOperationKey operationKey) {
        return port.createExpenseDraft(context.actor(), command, operationKey);
    }
}
