package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
@RequiredArgsConstructor
public final class HrEmployeeQueryToolHandler implements ToolHandler {
    private final HrEmployeeToolPort port;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return ToolCode.HR_EMPLOYEE_QUERY.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        return ToolJsonOutput.omitNulls(objectMapper.valueToTree(
                port.get(context.userId(), requiredLong(arguments, "employeeId", 1))));
    }
}
