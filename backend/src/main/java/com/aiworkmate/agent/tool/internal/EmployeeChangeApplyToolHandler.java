package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class EmployeeChangeApplyToolHandler extends TypedWriteToolHandler<
        EmployeeChangeToolPort.ApplicationCommand, EmployeeChangeToolPort.ApplicationResult> {
    private final EmployeeChangeToolPort port;

    public EmployeeChangeApplyToolHandler(EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_CHANGE_APPLY, objectMapper);
        this.port = port;
    }

    @Override
    protected EmployeeChangeToolPort.ApplicationCommand parseArguments(JsonNode arguments) {
        return new EmployeeChangeToolPort.ApplicationCommand(
                requiredLong(arguments, "employeeUserId", 1),
                requiredText(arguments, "changeType"),
                requiredDate(arguments, "effectiveDate"),
                optionalPositiveLong(arguments, "targetDepartmentId"),
                optionalPositiveLong(arguments, "targetPositionId"),
                optionalPositiveLong(arguments, "targetSupervisorUserId"),
                requiredLong(arguments, "reviewApproverUserId", 1),
                requiredText(arguments, "reason"));
    }

    @Override
    protected EmployeeChangeToolPort.ApplicationResult invoke(
            TrustedToolContext context, EmployeeChangeToolPort.ApplicationCommand command) {
        return port.apply(context.actor(), command, stableOperationKey(context));
    }
}
