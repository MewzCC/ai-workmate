package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class HrEmployeeQueryToolHandler extends TypedReadToolHandler<Long, HrEmployeeToolPort.Employee> {
    private final HrEmployeeToolPort port;

    public HrEmployeeQueryToolHandler(HrEmployeeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_EMPLOYEE_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected Long parseArguments(JsonNode arguments) {
        return requiredLong(arguments, "employeeId", 1);
    }

    @Override protected HrEmployeeToolPort.Employee invoke(TrustedToolContext context, Long employeeId) {
        return port.get(context.actor(), employeeId);
    }
}
