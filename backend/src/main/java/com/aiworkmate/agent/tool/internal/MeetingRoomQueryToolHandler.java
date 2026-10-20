package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingRoomToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class MeetingRoomQueryToolHandler extends TypedReadToolHandler<MeetingRoomToolPort.Query, ToolPage<MeetingRoomToolPort.Room>> {
    private final MeetingRoomToolPort port;
    public MeetingRoomQueryToolHandler(MeetingRoomToolPort port, ObjectMapper mapper) {
        super(ToolCode.MEETING_ROOM_QUERY, mapper); this.port = port;
    }
    @Override protected MeetingRoomToolPort.Query parseArguments(JsonNode args) {
        return new MeetingRoomToolPort.Query(optionalText(args, "keyword"), MeetingRoomToolArguments.status(args, false), pageNumber(args), pageSize(args));
    }
    @Override protected ToolPage<MeetingRoomToolPort.Room> invoke(TrustedToolContext context, MeetingRoomToolPort.Query query) {
        return port.query(context.actor(), query);
    }
}
