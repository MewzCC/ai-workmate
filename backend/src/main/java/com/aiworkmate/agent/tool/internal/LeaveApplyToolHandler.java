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
public final class LeaveApplyToolHandler implements ToolHandler {
    private final LeaveToolPort leaveToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.LEAVE_APPLY.code();
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
        String operationKey = StableToolOperationKey.v1(context, ToolCode.LEAVE_APPLY);
        LeaveToolPort.WriteResult application = leaveToolPort.apply(
                context.actor(), request, operationKey);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", application.applicationId());
        output.put("status", application.status());
        output.put("version", application.version());
        output.put("approvalTaskId", application.approvalTaskId());
        return output;
    }

}
