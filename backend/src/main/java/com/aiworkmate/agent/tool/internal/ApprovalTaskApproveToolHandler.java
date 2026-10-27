package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalTaskApproveToolHandler extends AbstractApprovalDecisionToolHandler {
    public ApprovalTaskApproveToolHandler(ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_TASK_APPROVE, port, objectMapper);
    }

    @Override
    protected ApprovalTaskToolPort.WriteResult invoke(
            TrustedToolContext context, ApprovalTaskToolPort.DecisionCommand command) {
        return port.approve(context.actor(), command);
    }
}
