package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class SealWithdrawToolHandler
        extends TypedWriteToolHandler<SealToolPort.VersionCommand, SealToolPort.StatusResult> {
    private final SealToolPort port;

    public SealWithdrawToolHandler(SealToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.SEAL_WITHDRAW, objectMapper);
        this.port = port;
    }

    @Override
    protected SealToolPort.VersionCommand parseArguments(JsonNode arguments) {
        return new SealToolPort.VersionCommand(
                requiredLong(arguments, "usageId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1));
    }

    @Override
    protected SealToolPort.StatusResult invoke(
            TrustedToolContext context, SealToolPort.VersionCommand command) {
        return port.withdraw(context.actor(), command);
    }
}
