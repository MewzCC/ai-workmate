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
public final class DictionaryTypeUpdateToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.UpdateTypeCommand, DictionaryToolPort.UpdateTypeResult> {
    private final DictionaryToolPort port;

    public DictionaryTypeUpdateToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_TYPE_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.UpdateTypeCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.UpdateTypeCommand(requiredText(arguments, "code"),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "name"), optionalTextPreservingEmpty(arguments, "description"),
                optionalInt(arguments, "sortOrder", 0, 9999));
    }

    @Override
    protected DictionaryToolPort.UpdateTypeResult invoke(
            TrustedToolContext context, DictionaryToolPort.UpdateTypeCommand command) {
        return port.updateType(context.actor(), command);
    }
}
