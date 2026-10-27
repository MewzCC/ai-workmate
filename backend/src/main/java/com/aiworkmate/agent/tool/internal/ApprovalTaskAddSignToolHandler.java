package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ApprovalTaskAddSignToolHandler
        extends TypedWriteToolHandler<ApprovalTaskToolPort.AddSignCommand, ApprovalTaskToolPort.WriteResult> {
    private final ApprovalTaskToolPort port;

    public ApprovalTaskAddSignToolHandler(ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_TASK_ADD_SIGN, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalTaskToolPort.AddSignCommand parseArguments(JsonNode arguments) {
        String mode = requiredText(arguments, "mode");
        if (!"PRE".equals(mode) && !"POST".equals(mode)) {
            throw new com.aiworkmate.common.BusinessException(com.aiworkmate.common.ErrorCode.REQUEST_INVALID);
        }
        return new ApprovalTaskToolPort.AddSignCommand(
                requiredLong(arguments, "taskId", 1), requiredLong(arguments, "targetUserId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1), mode,
                requiredText(arguments, "reason"));
    }

    @Override
    protected ApprovalTaskToolPort.WriteResult invoke(
            TrustedToolContext context, ApprovalTaskToolPort.AddSignCommand command) {
        return port.addSign(context.actor(), command);
    }
}
