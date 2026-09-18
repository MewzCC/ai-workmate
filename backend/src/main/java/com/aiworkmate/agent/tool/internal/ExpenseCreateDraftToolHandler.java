package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDecimal;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;

@Component
public final class ExpenseCreateDraftToolHandler extends TypedWriteToolHandler<
        FinanceToolPort.ExpenseDraft, FinanceToolPort.ExpenseDraftResult> {
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private final FinanceToolPort port;

    public ExpenseCreateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected FinanceToolPort.ExpenseDraft parseArguments(JsonNode arguments) {
        return new FinanceToolPort.ExpenseDraft(
                optionalDecimal(arguments, "amount", MIN_AMOUNT, MAX_AMOUNT, 2),
                optionalText(arguments, "category"),
                optionalDate(arguments, "expenseDate"),
                optionalText(arguments, "invoiceNumber"),
                optionalText(arguments, "reason"));
    }

    @Override
    protected FinanceToolPort.ExpenseDraftResult invoke(
            TrustedToolContext context, FinanceToolPort.ExpenseDraft command) {
        return port.createExpenseDraft(context.actor(), command, stableOperationKey(context));
    }
}
