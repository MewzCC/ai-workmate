package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class NotificationMineToolHandler
        extends TypedReadToolHandler<NotificationMineToolHandler.Query, NotificationToolPort.Page> {
    private final NotificationToolPort notificationToolPort;

    public NotificationMineToolHandler(NotificationToolPort notificationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.NOTIFICATION_MINE, objectMapper);
        this.notificationToolPort = notificationToolPort;
    }

    @Override protected Query parseArguments(JsonNode arguments) {
        return new Query(positiveInt(arguments, "page", 1, Integer.MAX_VALUE),
                positiveInt(arguments, "size", 20, 50));
    }

    @Override protected NotificationToolPort.Page invoke(TrustedToolContext context, Query query) {
        return notificationToolPort.mine(context.actor(), query.page(), query.size());
    }

    record Query(int page, int size) { }
}
