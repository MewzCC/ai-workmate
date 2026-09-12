package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ApprovalConfigurationQueryToolHandler implements ToolHandler {
    private static final int MAX_SIZE = 50;

    private final ApprovalConfigurationToolPort approvalConfigurationToolPort;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return "approval.configuration.query"; }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        ApprovalConfigurationToolPort.Resource resource;
        try {
            resource = ApprovalConfigurationToolPort.Resource.valueOf(arguments.path("resource").asText(""));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        int page = positive(arguments, "page", 1);
        int size = Math.min(MAX_SIZE, positive(arguments, "size", 20));
        var result = approvalConfigurationToolPort.query(context.userId(),
                new ApprovalConfigurationToolPort.Query(resource, text(arguments, "keyword"),
                        text(arguments, "status"), page, size));
        ObjectNode output = objectMapper.createObjectNode();
        var items = output.putArray("items");
        result.items().forEach(record -> {
            ObjectNode item = items.addObject();
            item.put("id", record.id());
            item.put("resource", record.resource().name());
            item.put("key", record.key());
            item.put("name", record.name());
            if (record.description() != null) item.put("description", record.description());
            item.put("status", record.status());
            item.put("version", record.version());
            if (record.formName() != null) item.put("formName", record.formName());
            if (record.ruleType() != null) item.put("ruleType", record.ruleType());
            if (record.priority() != null) item.put("priority", record.priority());
            item.put("updatedAt", record.updatedAt().toString());
        });
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }

    private int positive(JsonNode arguments, String field, int fallback) {
        if (!arguments.has(field)) return fallback;
        JsonNode value = arguments.get(field);
        if (!value.isIntegralNumber() || value.asInt() < 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return value.asInt();
    }

    private String text(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }
}
