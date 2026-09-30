package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationRemindToolHandler
        extends TypedVersionedWriteToolHandler<ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort port;

    public ApprovalApplicationRemindToolHandler(
            ApprovalApplicationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_REMIND, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.remind(context.actor(), applicationId, version);
    }
}
