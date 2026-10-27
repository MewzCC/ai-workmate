package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationCancelDraftToolHandler
        extends TypedVersionedWriteToolHandler<ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort port;

    public ApprovalApplicationCancelDraftToolHandler(
            ApprovalApplicationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_CANCEL_DRAFT, objectMapper, "applicationId");
        this.port = port;
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return port.cancelDraft(context.actor(), applicationId, version);
    }
}
