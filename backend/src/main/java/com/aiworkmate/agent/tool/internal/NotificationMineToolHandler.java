package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class NotificationMineToolHandler implements ToolHandler {
    private final NotificationToolPort notificationToolPort;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return ToolCode.NOTIFICATION_MINE.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        int page = positiveInt(arguments, "page", 1, Integer.MAX_VALUE);
        int size = positiveInt(arguments, "size", 20, 50);
        var result = notificationToolPort.mine(context.userId(), page, size);
        ObjectNode output = objectMapper.createObjectNode();
        var items = output.putArray("items");
        result.items().forEach(notification -> {
            ObjectNode item = items.addObject();
            item.put("id", notification.id());
            item.put("type", notification.type());
            item.put("title", notification.title());
            item.put("content", notification.content());
            if (notification.businessType() != null) item.put("businessType", notification.businessType());
            item.put("read", notification.read());
            item.put("createdAt", notification.createdAt().toString());
        });
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }
}
