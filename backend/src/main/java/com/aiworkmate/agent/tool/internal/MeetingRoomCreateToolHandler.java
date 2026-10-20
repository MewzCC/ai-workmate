package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingRoomToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class MeetingRoomCreateToolHandler extends TypedWriteToolHandler<MeetingRoomToolPort.Fields, MeetingRoomToolPort.Receipt> {
    private final MeetingRoomToolPort port;
    public MeetingRoomCreateToolHandler(MeetingRoomToolPort port, ObjectMapper mapper) {
        super(ToolCode.MEETING_ROOM_CREATE, mapper); this.port = port;
    }
    @Override protected MeetingRoomToolPort.Fields parseArguments(JsonNode args) { return MeetingRoomToolArguments.fields(args, true); }
    @Override protected MeetingRoomToolPort.Receipt invoke(TrustedToolContext context, MeetingRoomToolPort.Fields fields) {
        return port.create(context.actor(), fields);
    }
}
