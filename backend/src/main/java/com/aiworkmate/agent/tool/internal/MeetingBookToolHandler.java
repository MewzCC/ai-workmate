package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
@RequiredArgsConstructor
public final class MeetingBookToolHandler implements ToolHandler {
    private final MeetingToolPort port;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.MEETING_BOOK.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var command = new MeetingToolPort.BookCommand(
                requiredLong(arguments, "roomId", 1), requiredText(arguments, "title"),
                optionalText(arguments, "agenda"), requiredDateTime(arguments, "startAt"),
                requiredDateTime(arguments, "endAt"), requiredInt(arguments, "attendeeCount", 1, 10000));
        String operationKey = "agent:" + context.taskId() + ":" + context.stepId() + ":"
                + ToolCode.MEETING_BOOK.code() + ":v1";
        return objectMapper.valueToTree(port.book(context.actor(), command, operationKey));
    }
}
