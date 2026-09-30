package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalTaskCopyToolHandler extends AbstractApprovalParticipantToolHandler {
    public ApprovalTaskCopyToolHandler(ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_TASK_COPY, port, objectMapper);
    }

    @Override
    protected ApprovalTaskToolPort.WriteResult invoke(
            TrustedToolContext context, ApprovalTaskToolPort.ParticipantCommand command) {
        return port.copyTo(context.actor(), command);
    }
}
