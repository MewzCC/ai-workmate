package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetAssignmentToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class AssetDepartmentQueryToolHandler extends TypedReadToolHandler<AssetAssignmentToolPort.Query, AssetAssignmentToolPort.Page> {
    private final AssetAssignmentToolPort port;
    public AssetDepartmentQueryToolHandler(AssetAssignmentToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_DEPARTMENT_QUERY, mapper); this.port = port;
    }
    @Override protected AssetAssignmentToolPort.Query parseArguments(JsonNode args) {
        return new AssetAssignmentToolPort.Query(BoundedToolArguments.optionalText(args, "keyword"),
                BoundedToolArguments.pageNumber(args), BoundedToolArguments.positiveInt(args, "size", 20, 20));
    }
    @Override protected AssetAssignmentToolPort.Page invoke(TrustedToolContext context, AssetAssignmentToolPort.Query query) {
        return port.departments(context.actor(), query);
    }
}
