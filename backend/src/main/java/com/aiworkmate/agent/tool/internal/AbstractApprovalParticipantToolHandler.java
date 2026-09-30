package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

abstract class AbstractApprovalParticipantToolHandler
        extends TypedWriteToolHandler<ApprovalTaskToolPort.ParticipantCommand, ApprovalTaskToolPort.WriteResult> {
    protected final ApprovalTaskToolPort port;

    protected AbstractApprovalParticipantToolHandler(
            ToolCode code, ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(code, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalTaskToolPort.ParticipantCommand parseArguments(JsonNode arguments) {
        return new ApprovalTaskToolPort.ParticipantCommand(
                requiredLong(arguments, "taskId", 1),
                requiredLong(arguments, "targetUserId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "reason"));
    }
}
