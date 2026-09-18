package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

/** Shared closed command parser for one version-bound visitor lifecycle transition. */
abstract class VisitorVisitTransitionToolHandler
        extends TypedWriteToolHandler<VisitorToolPort.VisitCommand, VisitorToolPort.VisitResult> {
    protected VisitorVisitTransitionToolHandler(ToolCode code, ObjectMapper objectMapper) {
        super(code, objectMapper);
    }

    @Override
    protected final VisitorToolPort.VisitCommand parseArguments(JsonNode arguments) {
        return new VisitorToolPort.VisitCommand(
                requiredLong(arguments, "bookingId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "remark"));
    }

    @Override
    protected final VisitorToolPort.VisitResult invoke(
            TrustedToolContext context, VisitorToolPort.VisitCommand command) {
        return invokeVisit(context, command, stableOperationKey(context));
    }

    protected abstract VisitorToolPort.VisitResult invokeVisit(
            TrustedToolContext context, VisitorToolPort.VisitCommand command,
            ToolOperationKey operationKey);
}
