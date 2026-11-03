package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PageUiToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class PageUiPrepareToolHandler extends TypedReadToolHandler<PageUiToolPort.Command, PageUiToolPort.Prepared> {
    private final PageUiToolPort port;
    public PageUiPrepareToolHandler(PageUiToolPort port, ObjectMapper mapper) {
        super(ToolCode.PAGE_UI_PREPARE, mapper);
        this.port = port;
    }
    @Override protected PageUiToolPort.Command parseArguments(JsonNode arguments) {
        var fields = new java.util.ArrayList<PageUiToolPort.Field>();
        if (arguments.has("fields")) for (JsonNode field : arguments.path("fields")) {
            JsonNode value = field.path("value");
            Object scalar;
            if (value.isTextual()) scalar = value.textValue();
            else if (value.isNumber()) scalar = value.decimalValue();
            else if (value.isBoolean()) scalar = value.booleanValue();
            else throw new com.aiworkmate.common.BusinessException(com.aiworkmate.common.ErrorCode.REQUEST_INVALID);
            fields.add(new PageUiToolPort.Field(BoundedToolArguments.requiredText(field, "name"), scalar));
        }
        JsonNode identifier = arguments.get("recordId");
        Object recordId = identifier != null && identifier.isTextual() ? identifier.textValue()
                : BoundedToolArguments.optionalPositiveLong(arguments, "recordId");
        if (recordId != null && !PageUiToolPort.Command.validRecordId(recordId))
            throw new com.aiworkmate.common.BusinessException(com.aiworkmate.common.ErrorCode.REQUEST_INVALID);
        return new PageUiToolPort.Command(BoundedToolArguments.requiredText(arguments, "pageId"),
                BoundedToolArguments.requiredText(arguments, "code"), BoundedToolArguments.optionalText(arguments, "targetPageId"),
                recordId, fields);
    }
    @Override protected PageUiToolPort.Prepared invoke(TrustedToolContext context, PageUiToolPort.Command command) {
        return port.prepare(context.actor(), command);
    }
}
