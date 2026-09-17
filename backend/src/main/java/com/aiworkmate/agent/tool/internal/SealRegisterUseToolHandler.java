package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class SealRegisterUseToolHandler
        extends TypedWriteToolHandler<SealToolPort.UseCommand, SealToolPort.UseResult> {
    private final SealToolPort port;

    public SealRegisterUseToolHandler(SealToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.SEAL_REGISTER_USE, objectMapper);
        this.port = port;
    }

    @Override
    protected SealToolPort.UseCommand parseArguments(JsonNode arguments) {
        return new SealToolPort.UseCommand(
                requiredLong(arguments, "usageId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredInt(arguments, "actualCopies", 1, 1000),
                optionalText(arguments, "remark"));
    }

    @Override
    protected SealToolPort.UseResult invoke(
            TrustedToolContext context, SealToolPort.UseCommand command) {
        return port.registerUse(context.actor(), command, stableOperationKey(context));
    }
}
