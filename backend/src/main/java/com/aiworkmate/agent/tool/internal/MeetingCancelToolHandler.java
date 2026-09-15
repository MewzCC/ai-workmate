package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
@RequiredArgsConstructor
public final class MeetingCancelToolHandler implements ToolHandler {
    private final MeetingToolPort port;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.MEETING_CANCEL.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var command = new MeetingToolPort.CancelCommand(
                requiredLong(arguments, "bookingId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "reason"));
        String operationKey = "agent:" + context.taskId() + ":" + context.stepId() + ":"
                + ToolCode.MEETING_CANCEL.code() + ":v1";
        return objectMapper.valueToTree(port.cancel(context.actor(), command, operationKey));
    }
}
