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
public final class SealReturnToolHandler
        extends TypedWriteToolHandler<SealToolPort.ReturnCommand, SealToolPort.StatusResult> {
    private final SealToolPort port;

    public SealReturnToolHandler(SealToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.SEAL_RETURN, objectMapper);
        this.port = port;
    }

    @Override
    protected SealToolPort.ReturnCommand parseArguments(JsonNode arguments) {
        return new SealToolPort.ReturnCommand(
                requiredLong(arguments, "usageId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "remark"));
    }

    @Override
    protected SealToolPort.StatusResult invoke(
            TrustedToolContext context, SealToolPort.ReturnCommand command) {
        return port.returnSeal(context.actor(), command);
    }
}
