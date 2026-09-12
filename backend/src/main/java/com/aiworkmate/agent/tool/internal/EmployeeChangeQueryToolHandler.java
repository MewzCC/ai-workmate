package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class EmployeeChangeQueryToolHandler implements ToolHandler {
    private final EmployeeChangeToolPort port;
    private final ObjectMapper objectMapper;
    @Override public String toolCode() { return ToolCode.HR_CHANGE_QUERY.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var result = port.query(context.userId(), new EmployeeChangeToolPort.Query(
                optionalText(arguments, "status"), optionalText(arguments, "changeType"),
                optionalText(arguments, "keyword"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50)));
        return ToolJsonOutput.omitNulls(objectMapper.valueToTree(result));
    }
}
