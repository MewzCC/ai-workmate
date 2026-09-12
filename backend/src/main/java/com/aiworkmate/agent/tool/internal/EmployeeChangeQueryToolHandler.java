package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class EmployeeChangeQueryToolHandler
        extends TypedReadToolHandler<EmployeeChangeToolPort.Query, EmployeeChangeToolPort.Page> {
    private final EmployeeChangeToolPort port;

    public EmployeeChangeQueryToolHandler(EmployeeChangeToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.HR_CHANGE_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected EmployeeChangeToolPort.Query parseArguments(JsonNode arguments) {
        return new EmployeeChangeToolPort.Query(
                optionalText(arguments, "status"), optionalText(arguments, "changeType"),
                optionalText(arguments, "keyword"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50));
    }

    @Override protected EmployeeChangeToolPort.Page invoke(
            TrustedToolContext context, EmployeeChangeToolPort.Query query) {
        return port.query(context.userId(), query);
    }
}
