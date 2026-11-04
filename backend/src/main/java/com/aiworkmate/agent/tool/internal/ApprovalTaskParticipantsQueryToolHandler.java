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
    record Query(long taskId, String keyword, int page, int size, boolean approversOnly) { }
    @Override protected Query parseArguments(JsonNode arguments) {
        return new Query(BoundedToolArguments.requiredLong(arguments, "taskId", 1),
                BoundedToolArguments.optionalText(arguments, "keyword"),
                arguments.has("page") ? BoundedToolArguments.requiredInt(arguments, "page", 1, 10000) : 1,
                arguments.has("size") ? BoundedToolArguments.requiredInt(arguments, "size", 1, 50) : 20,
                Boolean.TRUE.equals(BoundedToolArguments.optionalBoolean(arguments, "approversOnly")));
    }
    @Override protected ApprovalTaskToolPort.Participants invoke(TrustedToolContext context, Query query) {
        return port.participants(context.actor(), query.taskId(), new ApprovalTaskToolPort.ParticipantQuery(
                query.keyword(), query.page(), query.size(), query.approversOnly()));
    }
}
