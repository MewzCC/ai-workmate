package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.capability.PageCapabilityDefinition;
import com.aiworkmate.agent.capability.PageContextField;
import com.aiworkmate.agent.capability.PageContextValueType;
import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
@Component
@RequiredArgsConstructor
public class PageContextFilter {
    private final ObjectMapper objectMapper;
    private final AgentRuntimeProperties properties;
    private final PageCapabilityCatalog pageCapabilityCatalog;

    public JsonNode filter(String pageId, JsonNode context) {
        ObjectNode result = objectMapper.createObjectNode();
        if (context == null || context.isNull()) return result;
        PageCapabilityDefinition capability = pageCapabilityCatalog.find(pageId).orElse(null);
        if (capability == null) return result;
        int maxBytes = Math.min(properties.getLimits().getPageContextMaxBytes(), capability.contextSchema().maxBytes());
        int maxDepth = Math.min(properties.getLimits().getPageContextMaxDepth(), capability.contextSchema().maxDepth());
        if (!context.isObject() || context.toString().getBytes(StandardCharsets.UTF_8).length > maxBytes
                || depth(context, 1) > maxDepth) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        capability.contextSchema().fields().forEach(field -> copyAllowedScalar(context, result, field));
        return result;
    }

    private void copyAllowedScalar(JsonNode context, ObjectNode result, PageContextField field) {
        JsonNode value = context.get(field.name());
        if (value == null) return;
        boolean allowed = switch (field.valueType()) {
            case STRING -> value.isTextual() && value.textValue().length() <= field.maxLength();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
        };
        if (allowed) result.set(field.name(), value);
    }

    private int depth(JsonNode node, int current) {
        if (node == null || !node.isContainerNode()) return current;
        int max = current;
        for (JsonNode child : node) max = Math.max(max, depth(child, current + 1));
        return max;
    }
}
