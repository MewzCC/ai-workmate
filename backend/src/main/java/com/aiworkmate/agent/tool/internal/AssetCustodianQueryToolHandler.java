package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetAssignmentToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class AssetCustodianQueryToolHandler extends TypedReadToolHandler<AssetAssignmentToolPort.CustodianQuery, AssetAssignmentToolPort.Page> {
    private final AssetAssignmentToolPort port;
    public AssetCustodianQueryToolHandler(AssetAssignmentToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_CUSTODIAN_QUERY, mapper); this.port = port;
    }
    @Override protected AssetAssignmentToolPort.CustodianQuery parseArguments(JsonNode args) {
        return new AssetAssignmentToolPort.CustodianQuery(BoundedToolArguments.requiredLong(args, "departmentId", 1),
                BoundedToolArguments.optionalText(args, "keyword"), BoundedToolArguments.pageNumber(args),
                BoundedToolArguments.positiveInt(args, "size", 20, 20));
    }
    @Override protected AssetAssignmentToolPort.Page invoke(TrustedToolContext context, AssetAssignmentToolPort.CustodianQuery query) {
        return port.custodians(context.actor(), query);
    }
}
