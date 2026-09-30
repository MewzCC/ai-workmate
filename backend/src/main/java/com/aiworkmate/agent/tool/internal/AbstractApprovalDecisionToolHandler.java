package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

abstract class AbstractApprovalDecisionToolHandler
        extends TypedWriteToolHandler<ApprovalTaskToolPort.DecisionCommand, ApprovalTaskToolPort.WriteResult> {
    protected final ApprovalTaskToolPort port;

    protected AbstractApprovalDecisionToolHandler(
            ToolCode code, ApprovalTaskToolPort port, ObjectMapper objectMapper) {
        super(code, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalTaskToolPort.DecisionCommand parseArguments(JsonNode arguments) {
        return new ApprovalTaskToolPort.DecisionCommand(
                requiredLong(arguments, "taskId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "comment"));
    }
}
