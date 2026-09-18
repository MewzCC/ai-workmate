package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class LeaveCreateDraftToolHandler extends TypedWriteToolHandler<LeaveToolPort.Draft, LeaveToolPort.WriteResult> {
    private final LeaveToolPort leaveToolPort;

    public LeaveCreateDraftToolHandler(LeaveToolPort leaveToolPort, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_CREATE_DRAFT, objectMapper);
        this.leaveToolPort = leaveToolPort;
    }

    @Override
    protected LeaveToolPort.Draft parseArguments(JsonNode arguments) {
        return new LeaveToolPort.Draft(
                requiredText(arguments, "leaveType"), optionalPositiveLong(arguments, "approverUserId"),
                requiredDate(arguments, "startDate"), requiredText(arguments, "startPeriod"),
                requiredDate(arguments, "endDate"), requiredText(arguments, "endPeriod"),
                requiredText(arguments, "reason"));
    }

    @Override
    protected LeaveToolPort.WriteResult invoke(TrustedToolContext context, LeaveToolPort.Draft command) {
        return leaveToolPort.createDraft(context.actor(), command, stableOperationKey(context));
    }

    @Override
    protected JsonNode serializeResult(LeaveToolPort.WriteResult created) {
        ObjectNode output = objectMapper().createObjectNode();
        output.put("applicationId", created.applicationId());
        output.put("status", created.status());
        output.put("version", created.version());
        return output;
    }
}
