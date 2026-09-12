package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
@RequiredArgsConstructor
public final class LeaveSubmitToolHandler implements ToolHandler {
    private final LeaveToolPort leaveToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "leave.submit";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        long applicationId = requiredLong(arguments, "applicationId", 1);
        int version = Math.toIntExact(requiredLong(arguments, "version", 0));
        LeaveToolPort.WriteResult submitted = leaveToolPort.submit(
                context.userId(), applicationId, version, context.taskId());
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", submitted.applicationId());
        output.put("status", submitted.status());
        output.put("version", submitted.version());
        return output;
    }

}
