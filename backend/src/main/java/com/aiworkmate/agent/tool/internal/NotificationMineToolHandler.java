package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageSize;

@Component
public final class NotificationMineToolHandler
        extends TypedReadToolHandler<NotificationMineToolHandler.Query, NotificationToolPort.Page> {
    private final NotificationToolPort notificationToolPort;

    public NotificationMineToolHandler(NotificationToolPort notificationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.NOTIFICATION_MINE, objectMapper);
        this.notificationToolPort = notificationToolPort;
    }

    @Override protected Query parseArguments(JsonNode arguments) {
        return new Query(pageNumber(arguments, Integer.MAX_VALUE),
                pageSize(arguments));
    }

    @Override protected NotificationToolPort.Page invoke(TrustedToolContext context, Query query) {
        return notificationToolPort.mine(context.actor(), query.page(), query.size());
    }

    record Query(int page, int size) { }
}
