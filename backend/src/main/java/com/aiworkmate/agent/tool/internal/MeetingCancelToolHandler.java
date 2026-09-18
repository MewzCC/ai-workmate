package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class MeetingCancelToolHandler extends TypedWriteToolHandler<MeetingToolPort.CancelCommand, MeetingToolPort.CancelResult> {
    private final MeetingToolPort port;

    public MeetingCancelToolHandler(MeetingToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.MEETING_CANCEL, objectMapper);
        this.port = port;
    }

    @Override
    protected MeetingToolPort.CancelCommand parseArguments(JsonNode arguments) {
        return new MeetingToolPort.CancelCommand(
                requiredLong(arguments, "bookingId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "reason"));
    }

    @Override
    protected MeetingToolPort.CancelResult invoke(
            TrustedToolContext context, MeetingToolPort.CancelCommand command) {
        return port.cancel(context.actor(), command, stableOperationKey(context));
    }
}
