package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class NotificationMineToolHandler implements ToolHandler {
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return "notification.mine"; }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        int page = positive(arguments, "page", 1);
        int size = Math.min(50, positive(arguments, "size", 20));
        var result = notificationService.list(context.userId(), page, size);
        ObjectNode output = objectMapper.createObjectNode();
        var items = output.putArray("items");
        result.records().forEach(notification -> {
            ObjectNode item = items.addObject();
            item.put("id", notification.id());
            item.put("type", notification.type());
            item.put("title", notification.title());
            item.put("content", notification.content());
            if (notification.bizType() != null) item.put("businessType", notification.bizType());
            item.put("read", notification.read());
            item.put("createdAt", notification.createdAt().toString());
        });
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }

    private int positive(JsonNode arguments, String field, int fallback) {
        if (!arguments.has(field)) return fallback;
        int value = arguments.path(field).asInt(0);
        if (value < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return value;
    }
}
