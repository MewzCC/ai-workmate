package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.tool.internal.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class HandlerResolver {
    private final Map<HandlerKey, ToolHandler> handlers;

    public HandlerResolver(List<ToolHandler> handlers) {
        Map<HandlerKey, ToolHandler> resolved = new HashMap<>();
        for (ToolHandler handler : handlers) {
            HandlerKey key = new HandlerKey(handler.toolCode(), handler.handlerVersion());
            if (resolved.putIfAbsent(key, handler) != null) {
                throw new IllegalStateException("Duplicate tool handler registration");
            }
        }
        this.handlers = Map.copyOf(resolved);
    }

    Optional<ToolHandler> resolve(String toolCode, String handlerVersion) {
        return Optional.ofNullable(handlers.get(new HandlerKey(toolCode, handlerVersion)));
    }

    private record HandlerKey(String toolCode, String handlerVersion) {
    }
}
