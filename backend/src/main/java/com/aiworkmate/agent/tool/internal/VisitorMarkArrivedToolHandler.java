package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class VisitorMarkArrivedToolHandler extends VisitorVisitTransitionToolHandler {
    private final VisitorToolPort port;

    public VisitorMarkArrivedToolHandler(VisitorToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.VISITOR_MARK_ARRIVED, objectMapper);
        this.port = port;
    }

    @Override
    protected VisitorToolPort.VisitResult invokeVisit(
            TrustedToolContext context, VisitorToolPort.VisitCommand command,
            ToolOperationKey operationKey) {
        return port.markArrived(context.actor(), command, operationKey);
    }
}
