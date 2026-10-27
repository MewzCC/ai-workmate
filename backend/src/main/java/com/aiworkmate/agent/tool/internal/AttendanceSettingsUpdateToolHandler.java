package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalTime;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class AttendanceSettingsUpdateToolHandler
        extends TypedWriteToolHandler<AttendanceToolPort.SettingsUpdateCommand,
        AttendanceToolPort.SettingsUpdateResult> {
    private final AttendanceToolPort port;

    public AttendanceSettingsUpdateToolHandler(AttendanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ATTENDANCE_SETTINGS_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected AttendanceToolPort.SettingsUpdateCommand parseArguments(JsonNode arguments) {
        LocalTime start = time(arguments, "workStartTime");
        LocalTime end = time(arguments, "workEndTime");
        if (!start.isBefore(end) || !arguments.path("flexLinked").isBoolean()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new AttendanceToolPort.SettingsUpdateCommand(
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE), start, end,
                requiredInt(arguments, "startFlexMinutes", 0, 480),
                requiredInt(arguments, "endFlexMinutes", 0, 480),
                arguments.path("flexLinked").booleanValue());
    }

    @Override
    protected AttendanceToolPort.SettingsUpdateResult invoke(
            TrustedToolContext context, AttendanceToolPort.SettingsUpdateCommand command) {
        return port.updateSettings(context.actor(), command);
    }

    private LocalTime time(JsonNode arguments, String field) {
        try {
            return LocalTime.parse(requiredText(arguments, field));
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }
}
