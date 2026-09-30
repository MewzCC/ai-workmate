package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;

@Component
public final class AttendanceClockToolHandler
        extends TypedWriteToolHandler<String, AttendanceToolPort.ClockWriteResult> {
    private final AttendanceToolPort port;

    public AttendanceClockToolHandler(AttendanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ATTENDANCE_CLOCK, objectMapper);
        this.port = port;
    }

    @Override
    protected String parseArguments(JsonNode arguments) {
        return requiredEnum(arguments, "clockType", ClockType.class).name();
    }

    @Override
    protected AttendanceToolPort.ClockWriteResult invoke(
            TrustedToolContext context, String clockType) {
        return port.clock(context.actor(), clockType);
    }

    private enum ClockType { CLOCK_IN, CLOCK_OUT }
}
