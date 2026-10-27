package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalTextPreservingEmpty;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class DictionaryItemUpdateToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.UpdateItemCommand, DictionaryToolPort.UpdateItemResult> {
    private final DictionaryToolPort port;

    public DictionaryItemUpdateToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_ITEM_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.UpdateItemCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.UpdateItemCommand(requiredText(arguments, "typeCode"),
                requiredText(arguments, "value"),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "label"),
                optionalTextPreservingEmpty(arguments, "description"),
                optionalInt(arguments, "sortOrder", 0, 9999));
    }

    @Override
    protected DictionaryToolPort.UpdateItemResult invoke(
            TrustedToolContext context, DictionaryToolPort.UpdateItemCommand command) {
        return port.updateItem(context.actor(), command);
    }
}
