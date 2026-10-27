package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingRoomToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class MeetingRoomUpdateToolHandler extends TypedWriteToolHandler<MeetingRoomToolPort.Update, MeetingRoomToolPort.Receipt> {
    private final MeetingRoomToolPort port;
    public MeetingRoomUpdateToolHandler(MeetingRoomToolPort port, ObjectMapper mapper) {
        super(ToolCode.MEETING_ROOM_UPDATE, mapper); this.port = port;
    }
    @Override protected MeetingRoomToolPort.Update parseArguments(JsonNode args) {
        if (args.size() < 3) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return new MeetingRoomToolPort.Update(requiredLong(args, "roomId", 1),
                requiredInt(args, "version", 0, Integer.MAX_VALUE - 1), MeetingRoomToolArguments.fields(args, false));
    }
    @Override protected MeetingRoomToolPort.Receipt invoke(TrustedToolContext context, MeetingRoomToolPort.Update command) {
        return port.update(context.actor(), command);
    }
}
