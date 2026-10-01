package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class DictionaryTypeUpdateStatusToolHandler extends TypedWriteToolHandler<
        DictionaryToolPort.UpdateTypeStatusCommand, DictionaryToolPort.UpdateTypeResult> {
    private final DictionaryToolPort port;

    public DictionaryTypeUpdateStatusToolHandler(DictionaryToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.DICTIONARY_TYPE_UPDATE_STATUS, objectMapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.UpdateTypeStatusCommand parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.UpdateTypeStatusCommand(requiredText(arguments, "code"),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "status"));
    }

    @Override
    protected DictionaryToolPort.UpdateTypeResult invoke(
            TrustedToolContext context, DictionaryToolPort.UpdateTypeStatusCommand command) {
        return port.updateTypeStatus(context.actor(), command);
    }
}
