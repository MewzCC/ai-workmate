package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class VisitorLeaveToolHandler extends VisitorVisitTransitionToolHandler {
    private final VisitorToolPort port;

    public VisitorLeaveToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_LEAVE, objectMapper);
        this.port = port;
    }

    @Override
    protected VisitorToolPort.VisitResult invokeVisit(
            TrustedToolContext context, VisitorToolPort.VisitCommand command,
            ToolOperationKey operationKey) {
        return port.leave(context.actor(), command, operationKey);
    }
}
