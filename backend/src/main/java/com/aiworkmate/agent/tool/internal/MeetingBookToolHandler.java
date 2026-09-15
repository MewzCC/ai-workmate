package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class MeetingBookToolHandler extends TypedWriteToolHandler<MeetingToolPort.BookCommand, MeetingToolPort.WriteResult> {
    private final MeetingToolPort port;

    public MeetingBookToolHandler(MeetingToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.MEETING_BOOK, objectMapper);
        this.port = port;
    }

    @Override
    protected MeetingToolPort.BookCommand parseArguments(JsonNode arguments) {
        return new MeetingToolPort.BookCommand(
                requiredLong(arguments, "roomId", 1), requiredText(arguments, "title"),
                optionalText(arguments, "agenda"), requiredDateTime(arguments, "startAt"),
                requiredDateTime(arguments, "endAt"), requiredInt(arguments, "attendeeCount", 1, 10000));
    }

    @Override
    protected MeetingToolPort.WriteResult invoke(
            TrustedToolContext context, MeetingToolPort.BookCommand command) {
        return port.book(context.actor(), command, stableOperationKey(context));
    }
}
