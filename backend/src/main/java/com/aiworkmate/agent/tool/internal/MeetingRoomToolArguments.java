package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Fields;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

final class MeetingRoomToolArguments {
    private MeetingRoomToolArguments() { }
    static String status(JsonNode args, boolean required) {
        String value = required ? requiredText(args, "status") : optionalTextPreservingEmpty(args, "status");
        if (value != null && !Set.of("OPEN", "CLOSED").contains(value)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return value;
    }
    static Fields fields(JsonNode args, boolean create) {
        return new Fields(create ? requiredText(args, "code") : optionalTextPreservingEmpty(args, "code"),
                create ? requiredText(args, "name") : optionalTextPreservingEmpty(args, "name"),
                optionalTextPreservingEmpty(args, "location"), create ? Integer.valueOf(requiredInt(args, "capacity", 0, 10000))
                        : optionalInt(args, "capacity", 0, 10000),
                optionalTextPreservingEmpty(args, "facilities"), status(args, create), optionalTextPreservingEmpty(args, "remark"));
    }
}
