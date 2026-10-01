package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class DictionaryItemCreateToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.CreateItemCommand, DictionaryToolPort.CreateItemResult> {
    private final DictionaryToolPort port;

    public DictionaryItemCreateToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_ITEM_CREATE, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.CreateItemCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.CreateItemCommand(requiredText(arguments, "typeCode"),
                requiredText(arguments, "value"), requiredText(arguments, "label"),
                optionalText(arguments, "description"), optionalInt(arguments, "sortOrder", 0, 9999));
    }

    @Override
    protected DictionaryToolPort.CreateItemResult invoke(
            TrustedToolContext context, DictionaryToolPort.CreateItemCommand command) {
        return port.createItem(context.actor(), command);
    }
}
