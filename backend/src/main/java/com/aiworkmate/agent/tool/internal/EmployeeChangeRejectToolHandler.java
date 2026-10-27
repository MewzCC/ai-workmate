package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class EmployeeChangeRejectToolHandler extends AbstractEmployeeChangeDecisionToolHandler {
    public EmployeeChangeRejectToolHandler(EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_CHANGE_REJECT, port, objectMapper);
    }

    @Override
    protected EmployeeChangeToolPort.DecisionCommand parseArguments(JsonNode arguments) {
        EmployeeChangeToolPort.DecisionCommand command = super.parseArguments(arguments);
        if (command.comment() == null) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return command;
    }

    @Override
    protected EmployeeChangeToolPort.ChangeActionResult invoke(
            TrustedToolContext context, EmployeeChangeToolPort.DecisionCommand command) {
        return port.reject(context.actor(), command);
    }
}
