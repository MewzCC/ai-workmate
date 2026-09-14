package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
@RequiredArgsConstructor
public final class LeaveCreateDraftToolHandler implements ToolHandler {
    private final LeaveToolPort leaveToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.LEAVE_CREATE_DRAFT.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        LeaveToolPort.Draft request = new LeaveToolPort.Draft(
                requiredText(arguments, "leaveType"), optionalPositiveLong(arguments, "approverUserId"),
                requiredDate(arguments, "startDate"), requiredText(arguments, "startPeriod"),
                requiredDate(arguments, "endDate"), requiredText(arguments, "endPeriod"),
                requiredText(arguments, "reason"));
        String operationKey = "agent:" + context.taskId() + ":" + context.stepId() + ":"
                + ToolCode.LEAVE_CREATE_DRAFT.code() + ":v1";
        LeaveToolPort.WriteResult created = leaveToolPort.createDraft(
                context.actor(), request, operationKey);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", created.applicationId());
        output.put("status", created.status());
        output.put("version", created.version());
        return output;
    }

}
