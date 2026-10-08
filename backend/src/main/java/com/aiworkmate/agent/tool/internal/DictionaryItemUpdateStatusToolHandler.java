package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class DictionaryItemUpdateStatusToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.UpdateItemStatusCommand, DictionaryToolPort.UpdateItemResult> {
    private final DictionaryToolPort port;

    public DictionaryItemUpdateStatusToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_ITEM_UPDATE_STATUS, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.UpdateItemStatusCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.UpdateItemStatusCommand(requiredText(arguments, "typeCode"),
                requiredText(arguments, "value"),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"));
    }

    @Override
    protected DictionaryToolPort.UpdateItemResult invoke(
            TrustedToolContext context, DictionaryToolPort.UpdateItemStatusCommand command) {
        return port.updateItemStatus(context.actor(), command);
    }
}
