package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
@RequiredArgsConstructor
public final class AttendanceReissueApplyToolHandler implements ToolHandler {
    private final AttendanceToolPort port;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.ATTENDANCE_REISSUE_APPLY.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var command = new AttendanceToolPort.ReissueCommand(
                requiredDate(arguments, "clockDate"),
                requiredEnum(arguments, "clockType", ClockType.class).name(),
                requiredText(arguments, "reason"));
        String operationKey = StableToolOperationKey.v1(context, ToolCode.ATTENDANCE_REISSUE_APPLY);
        return objectMapper.valueToTree(port.submitReissue(context.actor(), command, operationKey));
    }

    private enum ClockType { CLOCK_IN, CLOCK_OUT }
}
