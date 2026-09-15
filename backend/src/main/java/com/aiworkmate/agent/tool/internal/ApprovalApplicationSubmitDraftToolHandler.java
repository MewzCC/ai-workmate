package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationSubmitDraftToolHandler extends TypedVersionedWriteToolHandler<ApprovalApplicationToolPort.WriteResult> {
    private final ApprovalApplicationToolPort approvalApplicationToolPort;

    public ApprovalApplicationSubmitDraftToolHandler(
            ApprovalApplicationToolPort approvalApplicationToolPort, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_APPLICATION_SUBMIT_DRAFT, objectMapper, "applicationId");
        this.approvalApplicationToolPort = approvalApplicationToolPort;
    }

    @Override
    protected ApprovalApplicationToolPort.WriteResult invokeVersioned(
            TrustedToolContext context, long applicationId, int version) {
        return approvalApplicationToolPort.submitDraft(context.actor(), applicationId, version);
    }
}
