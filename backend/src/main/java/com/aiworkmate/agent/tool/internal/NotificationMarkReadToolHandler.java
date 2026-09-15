package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
@RequiredArgsConstructor
public final class NotificationMarkReadToolHandler implements ToolHandler {
    private final NotificationToolPort port;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.NOTIFICATION_MARK_READ.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        long notificationId = requiredLong(arguments, "notificationId", 1);
        return objectMapper.valueToTree(port.markRead(context.actor(), notificationId));
    }
}
