package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class EmployeeChangeWithdrawToolHandler extends TypedWriteToolHandler<
        EmployeeChangeToolPort.VersionedCommand, EmployeeChangeToolPort.ChangeActionResult> {
    private final EmployeeChangeToolPort port;

    public EmployeeChangeWithdrawToolHandler(EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_CHANGE_WITHDRAW, objectMapper);
        this.port = port;
    }

    @Override
    protected EmployeeChangeToolPort.VersionedCommand parseArguments(JsonNode arguments) {
        return new EmployeeChangeToolPort.VersionedCommand(
                requiredLong(arguments, "changeId", 1),
                requiredInt(arguments, "expectedVersion", 0, Integer.MAX_VALUE - 1));
    }

    @Override
    protected EmployeeChangeToolPort.ChangeActionResult invoke(
            TrustedToolContext context, EmployeeChangeToolPort.VersionedCommand command) {
        return port.withdraw(context.actor(), command);
    }
}
