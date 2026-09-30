package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public final class LeaveApplyToolHandler extends TypedOperationKeyWriteToolHandler<LeaveToolPort.Draft, LeaveToolPort.WriteResult> {
    private final LeaveToolPort leaveToolPort;

    public LeaveApplyToolHandler(LeaveToolPort leaveToolPort, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_APPLY, objectMapper);
        this.leaveToolPort = leaveToolPort;
    }

    @Override
    protected LeaveToolPort.Draft parseArguments(JsonNode arguments) {
        return LeaveDraftArguments.parse(arguments);
    }

    @Override
    protected LeaveToolPort.WriteResult invokeWithOperationKey(
            TrustedToolContext context, LeaveToolPort.Draft command, ToolOperationKey operationKey) {
        return leaveToolPort.apply(context.actor(), command, operationKey);
    }

    @Override
    protected JsonNode serializeResult(LeaveToolPort.WriteResult application) {
        ObjectNode output = objectMapper().createObjectNode();
        output.put("applicationId", application.applicationId());
        output.put("status", application.status());
        output.put("version", application.version());
        output.put("approvalTaskId", application.approvalTaskId());
        return output;
    }
}
