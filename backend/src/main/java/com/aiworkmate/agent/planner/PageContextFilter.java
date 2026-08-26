package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PageContextFilter {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "todo-list", Set.of("status", "from", "to", "page", "size"),
            "my-applications", Set.of("applicationId", "status", "page", "size"),
            "knowledge-base", Set.of("query", "topK", "minScore"),
            "message-center", Set.of("page", "size"),
            "dashboard", Set.of("status", "page", "size"),
            "ai-workspace", Set.of()
    );
    private final ObjectMapper objectMapper;
    private final AgentRuntimeProperties properties;

    public JsonNode filter(String pageId, JsonNode context) {
        ObjectNode result = objectMapper.createObjectNode();
        if (context == null || context.isNull()) return result;
        if (!context.isObject() || context.toString().getBytes(StandardCharsets.UTF_8).length
                > properties.getLimits().getPageContextMaxBytes()
                || depth(context, 1) > properties.getLimits().getPageContextMaxDepth()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        Set<String> fields = ALLOWED.getOrDefault(pageId, Set.of());
        fields.forEach(field -> {
            JsonNode value = context.get(field);
            if (value != null && (value.isTextual() || value.isNumber() || value.isBoolean())) result.set(field, value);
        });
        return result;
    }

    private int depth(JsonNode node, int current) {
        if (node == null || !node.isContainerNode()) return current;
        int max = current;
        for (JsonNode child : node) max = Math.max(max, depth(child, current + 1));
        return max;
    }
}
