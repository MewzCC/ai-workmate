package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class VisitorApplyToolHandler
        extends TypedWriteToolHandler<VisitorToolPort.ApplicationCommand, VisitorToolPort.ApplicationResult> {
    private final VisitorToolPort port;

    public VisitorApplyToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_APPLY, objectMapper);
        this.port = port;
    }

    @Override
    protected VisitorToolPort.ApplicationCommand parseArguments(JsonNode arguments) {
        return new VisitorToolPort.ApplicationCommand(
                requiredText(arguments, "visitorName"), optionalText(arguments, "visitorCompany"),
                optionalText(arguments, "visitorPhone"), requiredText(arguments, "purpose"),
                requiredLong(arguments, "hostUserId", 1), requiredDateTime(arguments, "expectedVisitAt"),
                optionalDateTime(arguments, "expectedLeaveAt"), optionalText(arguments, "plateNumber"),
                requiredInt(arguments, "partySize", 1, 1000));
    }

    @Override
    protected VisitorToolPort.ApplicationResult invoke(
            TrustedToolContext context, VisitorToolPort.ApplicationCommand command) {
        return port.apply(context.actor(), command, stableOperationKey(context));
    }
}
