package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class SealApplyToolHandler
        extends TypedOperationKeyWriteToolHandler<SealToolPort.ApplicationCommand, SealToolPort.ApplicationResult> {
    private final SealToolPort port;

    public SealApplyToolHandler(SealToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.SEAL_APPLY, objectMapper);
        this.port = port;
    }

    @Override
    protected SealToolPort.ApplicationCommand parseArguments(JsonNode arguments) {
        return new SealToolPort.ApplicationCommand(
                requiredText(arguments, "sealType"), requiredText(arguments, "documentTitle"),
                requiredText(arguments, "usageReason"), requiredInt(arguments, "copies", 1, 1000));
    }

    @Override
    protected SealToolPort.ApplicationResult invokeWithOperationKey(
            TrustedToolContext context, SealToolPort.ApplicationCommand command,
            ToolOperationKey operationKey) {
        return port.apply(context.actor(), command, operationKey);
    }
}
