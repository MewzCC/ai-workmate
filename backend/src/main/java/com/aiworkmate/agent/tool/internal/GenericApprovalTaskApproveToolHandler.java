package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GenericApprovalTaskApproveToolHandler
        extends TypedWriteToolHandler<GenericApprovalTaskToolPort.DecisionCommand, GenericApprovalTaskToolPort.WriteResult> {
    private final GenericApprovalTaskToolPort port;

    public GenericApprovalTaskApproveToolHandler(GenericApprovalTaskToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_GENERIC_TASK_APPROVE, mapper);
        this.port = port;
    }
    @Override protected GenericApprovalTaskToolPort.DecisionCommand parseArguments(JsonNode args) {
        return new GenericApprovalTaskToolPort.DecisionCommand(BoundedToolArguments.requiredLong(args, "taskId", 1),
                BoundedToolArguments.requiredInt(args, "version", 0, Integer.MAX_VALUE - 1),
                BoundedToolArguments.optionalText(args, "comment"));
    }
    @Override protected GenericApprovalTaskToolPort.WriteResult invoke(TrustedToolContext context, GenericApprovalTaskToolPort.DecisionCommand command) {
        return port.approve(context.actor(), command);
    }
}
