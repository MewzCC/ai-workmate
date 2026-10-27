package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalApplicationDetailQueryToolHandler
        extends TypedReadToolHandler<ApprovalApplicationToolPort.DetailQuery, ApprovalApplicationToolPort.Detail> {
    private final ApprovalApplicationToolPort port;
    public ApprovalApplicationDetailQueryToolHandler(ApprovalApplicationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_APPLICATION_DETAIL_QUERY, mapper);
        this.port = port;
    }
    @Override protected ApprovalApplicationToolPort.DetailQuery parseArguments(JsonNode args) {
        Integer page = BoundedToolArguments.optionalInt(args, "fieldPage", 1, 2);
        return new ApprovalApplicationToolPort.DetailQuery(BoundedToolArguments.requiredLong(args, "applicationId", 1),
                page == null ? 1 : page);
    }
    @Override protected ApprovalApplicationToolPort.Detail invoke(TrustedToolContext context, ApprovalApplicationToolPort.DetailQuery query) {
        return port.detailMine(context.actor(), query);
    }
}
