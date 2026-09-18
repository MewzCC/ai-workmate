package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDecimal;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;

/** Shared bounded parser for expense draft creation and patch commands. */
final class ExpenseDraftArguments {
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private ExpenseDraftArguments() { }

    static FinanceToolPort.ExpenseDraft parse(JsonNode arguments) {
        return new FinanceToolPort.ExpenseDraft(
                optionalDecimal(arguments, "amount", MIN_AMOUNT, MAX_AMOUNT, 2),
                optionalText(arguments, "category"),
                optionalDate(arguments, "expenseDate"),
                optionalText(arguments, "invoiceNumber"),
                optionalText(arguments, "reason"));
    }
}
