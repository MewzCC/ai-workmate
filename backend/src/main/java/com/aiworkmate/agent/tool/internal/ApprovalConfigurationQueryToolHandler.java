package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;

@Component
@RequiredArgsConstructor
public final class ApprovalConfigurationQueryToolHandler implements ToolHandler {
    private static final int MAX_SIZE = 50;

    private final ApprovalConfigurationToolPort approvalConfigurationToolPort;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return ToolCode.APPROVAL_CONFIGURATION_QUERY.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        ApprovalConfigurationToolPort.Resource resource = requiredEnum(
                arguments, "resource", ApprovalConfigurationToolPort.Resource.class);
        int page = positiveInt(arguments, "page", 1, Integer.MAX_VALUE);
        int size = positiveInt(arguments, "size", 20, MAX_SIZE);
        var result = approvalConfigurationToolPort.query(context.userId(),
                new ApprovalConfigurationToolPort.Query(resource, optionalText(arguments, "keyword"),
                        optionalText(arguments, "status"), page, size));
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

}
