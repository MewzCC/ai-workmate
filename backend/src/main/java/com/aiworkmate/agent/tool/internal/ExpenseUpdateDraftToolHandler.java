package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class ExpenseUpdateDraftToolHandler extends TypedWriteToolHandler<
        ExpenseUpdateDraftToolHandler.Command, FinanceToolPort.ExpenseLifecycleResult> {
    private final FinanceToolPort port;

    public ExpenseUpdateDraftToolHandler(FinanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.EXPENSE_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected Command parseArguments(JsonNode arguments) {
        return new Command(
                requiredLong(arguments, "applicationId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                ExpenseDraftArguments.parse(arguments));
    }

    @Override
    protected FinanceToolPort.ExpenseLifecycleResult invoke(
            TrustedToolContext context, Command command) {
        return port.updateExpenseDraft(
                context.actor(), command.applicationId(), command.version(), command.patch());
    }

    record Command(long applicationId, int version, FinanceToolPort.ExpenseDraft patch) { }
}
