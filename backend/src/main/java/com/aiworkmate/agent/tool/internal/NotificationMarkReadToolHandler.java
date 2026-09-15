package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class NotificationMarkReadToolHandler extends TypedWriteToolHandler<Long, NotificationToolPort.ReadResult> {
    private final NotificationToolPort port;

    public NotificationMarkReadToolHandler(NotificationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.NOTIFICATION_MARK_READ, objectMapper);
        this.port = port;
    }

    @Override
    protected Long parseArguments(JsonNode arguments) {
        return requiredLong(arguments, "notificationId", 1);
    }

    @Override
    protected NotificationToolPort.ReadResult invoke(TrustedToolContext context, Long notificationId) {
        return port.markRead(context.actor(), notificationId);
    }
}
