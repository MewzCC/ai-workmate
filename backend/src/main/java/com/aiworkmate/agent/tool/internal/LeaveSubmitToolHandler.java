package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public final class LeaveSubmitToolHandler extends TypedVersionedWriteToolHandler<LeaveToolPort.WriteResult> {
    private final LeaveToolPort leaveToolPort;

    public LeaveSubmitToolHandler(LeaveToolPort leaveToolPort, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_SUBMIT, objectMapper, "applicationId");
        this.leaveToolPort = leaveToolPort;
    }

    @Override
    protected LeaveToolPort.WriteResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return leaveToolPort.submit(context.actor(), applicationId, version);
    }

    @Override
    protected JsonNode serializeResult(LeaveToolPort.WriteResult submitted) {
        ObjectNode output = objectMapper().createObjectNode();
        output.put("applicationId", submitted.applicationId());
        output.put("status", submitted.status());
        output.put("version", submitted.version());
        return output;
    }
}
