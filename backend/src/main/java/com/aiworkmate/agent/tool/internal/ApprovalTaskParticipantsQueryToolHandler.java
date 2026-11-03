package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalTaskParticipantsQueryToolHandler
        extends TypedReadToolHandler<ApprovalTaskParticipantsQueryToolHandler.Query, ApprovalTaskToolPort.Participants> {
    private final ApprovalTaskToolPort port;
    public ApprovalTaskParticipantsQueryToolHandler(ApprovalTaskToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_TASK_PARTICIPANTS_QUERY, mapper);
        this.port = port;
    }
    record Query(long taskId, String keyword) { }
    @Override protected Query parseArguments(JsonNode arguments) {
        return new Query(BoundedToolArguments.requiredLong(arguments, "taskId", 1),
                BoundedToolArguments.optionalText(arguments, "keyword"));
    }
    @Override protected ApprovalTaskToolPort.Participants invoke(TrustedToolContext context, Query query) {
        return port.participants(context.actor(), query.taskId(), query.keyword());
    }
}
