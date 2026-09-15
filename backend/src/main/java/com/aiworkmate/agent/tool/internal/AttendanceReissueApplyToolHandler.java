package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class AttendanceReissueApplyToolHandler extends TypedWriteToolHandler<AttendanceToolPort.ReissueCommand, AttendanceToolPort.ReissueWriteResult> {
    private final AttendanceToolPort port;

    public AttendanceReissueApplyToolHandler(AttendanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ATTENDANCE_REISSUE_APPLY, objectMapper);
        this.port = port;
    }

    @Override
    protected AttendanceToolPort.ReissueCommand parseArguments(JsonNode arguments) {
        return new AttendanceToolPort.ReissueCommand(
                requiredDate(arguments, "clockDate"),
                requiredEnum(arguments, "clockType", ClockType.class).name(),
                requiredText(arguments, "reason"));
    }

    @Override
    protected AttendanceToolPort.ReissueWriteResult invoke(
            TrustedToolContext context, AttendanceToolPort.ReissueCommand command) {
        return port.submitReissue(context.actor(), command, stableOperationKey(context));
    }

    private enum ClockType { CLOCK_IN, CLOCK_OUT }
}
