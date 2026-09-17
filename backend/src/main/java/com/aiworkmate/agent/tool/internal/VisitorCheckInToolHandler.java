package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class VisitorCheckInToolHandler
        extends TypedWriteToolHandler<VisitorToolPort.VisitCommand, VisitorToolPort.VisitResult> {
    private final VisitorToolPort port;

    public VisitorCheckInToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_CHECK_IN, objectMapper);
        this.port = port;
    }

    @Override
    protected VisitorToolPort.VisitCommand parseArguments(JsonNode arguments) {
        return new VisitorToolPort.VisitCommand(
                requiredLong(arguments, "bookingId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "remark"));
    }

    @Override
    protected VisitorToolPort.VisitResult invoke(
            TrustedToolContext context, VisitorToolPort.VisitCommand command) {
        return port.checkIn(context.actor(), command, stableOperationKey(context));
    }
}
