package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GenericApprovalTaskDetailQueryToolHandler
        extends TypedReadToolHandler<GenericApprovalTaskToolPort.DetailQuery, GenericApprovalTaskToolPort.Detail> {
    private final GenericApprovalTaskToolPort port;

    public GenericApprovalTaskDetailQueryToolHandler(GenericApprovalTaskToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_GENERIC_TASK_DETAIL_QUERY, mapper);
        this.port = port;
    }
    @Override protected GenericApprovalTaskToolPort.DetailQuery parseArguments(JsonNode args) {
        Integer page = BoundedToolArguments.optionalInt(args, "fieldPage", 1, 25);
        return new GenericApprovalTaskToolPort.DetailQuery(BoundedToolArguments.requiredLong(args, "taskId", 1), page == null ? 1 : page);
    }
    @Override protected GenericApprovalTaskToolPort.Detail invoke(TrustedToolContext context, GenericApprovalTaskToolPort.DetailQuery query) {
        return port.detailAssigned(context.actor(), query);
    }
}
