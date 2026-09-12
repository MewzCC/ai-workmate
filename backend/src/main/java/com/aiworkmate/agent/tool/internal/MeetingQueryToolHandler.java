package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class MeetingQueryToolHandler extends TypedReadToolHandler<MeetingToolPort.Query, MeetingToolPort.Result> {
    private final MeetingToolPort port;

    public MeetingQueryToolHandler(MeetingToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.MEETING_QUERY, objectMapper);
        this.port = port;
    }

    @Override protected MeetingToolPort.Query parseArguments(JsonNode arguments) {
        return new MeetingToolPort.Query(
                optionalText(arguments, "keyword"), optionalText(arguments, "roomStatus"),
                optionalDateTime(arguments, "from"), optionalDateTime(arguments, "to"),
                optionalText(arguments, "bookingStatus"), positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50));
    }

    @Override protected MeetingToolPort.Result invoke(TrustedToolContext context, MeetingToolPort.Query query) {
        return port.query(context.userId(), query);
    }
}
