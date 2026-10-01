package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

abstract class AbstractEmployeeChangeDecisionToolHandler extends TypedWriteToolHandler<
        EmployeeChangeToolPort.DecisionCommand, EmployeeChangeToolPort.ChangeActionResult> {
    protected final EmployeeChangeToolPort port;

    protected AbstractEmployeeChangeDecisionToolHandler(
            ToolCode code, EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(code, objectMapper);
        this.port = port;
    }

    @Override
    protected EmployeeChangeToolPort.DecisionCommand parseArguments(JsonNode arguments) {
        return new EmployeeChangeToolPort.DecisionCommand(
                requiredLong(arguments, "changeId", 1),
                requiredInt(arguments, "expectedVersion", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "comment"));
    }
}
