package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class EmployeeChangeApproveToolHandler extends AbstractEmployeeChangeDecisionToolHandler {
    public EmployeeChangeApproveToolHandler(EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_CHANGE_APPROVE, port, objectMapper);
    }

    @Override
    protected EmployeeChangeToolPort.ChangeActionResult invoke(
            TrustedToolContext context, EmployeeChangeToolPort.DecisionCommand command) {
        return port.approve(context.actor(), command);
    }
}
