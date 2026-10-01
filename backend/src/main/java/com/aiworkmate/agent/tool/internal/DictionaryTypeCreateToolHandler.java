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
public final class DictionaryTypeCreateToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.CreateTypeCommand, DictionaryToolPort.CreateTypeResult> {
    private final DictionaryToolPort port;

    public DictionaryTypeCreateToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_TYPE_CREATE, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.CreateTypeCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.CreateTypeCommand(requiredText(arguments, "code"),
                requiredText(arguments, "name"), optionalText(arguments, "description"),
                optionalInt(arguments, "sortOrder", 0, 9999));
    }

    @Override
    protected DictionaryToolPort.CreateTypeResult invoke(
            TrustedToolContext context, DictionaryToolPort.CreateTypeCommand command) {
        return port.createType(context.actor(), command);
    }
}
