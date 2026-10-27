package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class VisitorWithdrawToolHandler
        extends TypedWriteToolHandler<VisitorToolPort.VersionCommand, VisitorToolPort.StatusResult> {
    private final VisitorToolPort port;

    public VisitorWithdrawToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_WITHDRAW, objectMapper);
        this.port = port;
    }

    @Override
    protected VisitorToolPort.VersionCommand parseArguments(JsonNode arguments) {
        return new VisitorToolPort.VersionCommand(
                requiredLong(arguments, "bookingId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1));
    }

    @Override
    protected VisitorToolPort.StatusResult invoke(
            TrustedToolContext context, VisitorToolPort.VersionCommand command) {
        return port.withdraw(context.actor(), command);
    }
}
