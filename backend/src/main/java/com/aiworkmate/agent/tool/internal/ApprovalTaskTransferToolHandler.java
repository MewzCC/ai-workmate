package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalTaskTransferToolHandler extends AbstractApprovalParticipantToolHandler {
    public ApprovalTaskTransferToolHandler(ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_TASK_TRANSFER, port, objectMapper);
    }

    @Override
    protected ApprovalTaskToolPort.WriteResult invoke(
            TrustedToolContext context, ApprovalTaskToolPort.ParticipantCommand command) {
        return port.transfer(context.actor(), command);
    }
}
