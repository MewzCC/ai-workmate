package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;

@Component
public final class AttendanceQueryToolHandler
        extends TypedReadToolHandler<AttendanceToolPort.Query, AttendanceToolPort.Result> {
    private final AttendanceToolPort port;

    public AttendanceQueryToolHandler(AttendanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ATTENDANCE_QUERY, objectMapper);
        this.port = port;
    }

    @Override
    protected AttendanceToolPort.Query parseArguments(JsonNode arguments) {
        return new AttendanceToolPort.Query(
                requiredEnum(arguments, "resource", AttendanceToolPort.Resource.class),
                optionalDate(arguments, "from"), optionalDate(arguments, "to"),
                optionalPositiveLong(arguments, "employeeId"), optionalText(arguments, "status"),
                optionalInt(arguments, "year", 2000, 2100), optionalInt(arguments, "month", 1, 12),
                positiveInt(arguments, "page", 1, 10000), positiveInt(arguments, "size", 20, 50));
    }

    @Override
    protected AttendanceToolPort.Result invoke(TrustedToolContext context, AttendanceToolPort.Query query) {
        return port.query(context.actor(), query);
    }
}
