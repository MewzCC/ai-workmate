package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;

@Component
public final class AssetCreateToolHandler extends TypedWriteToolHandler<AssetToolPort.Create, AssetToolPort.MetadataReceipt> {
    private final AssetToolPort port;
    public AssetCreateToolHandler(AssetToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_CREATE, mapper); this.port = port;
    }
    @Override protected AssetToolPort.Create parseArguments(JsonNode args) {
        return new AssetToolPort.Create(AssetMetadataToolArguments.fields(args, true), optionalPositiveLong(args, "departmentId"));
    }
    @Override protected AssetToolPort.MetadataReceipt invoke(TrustedToolContext context, AssetToolPort.Create command) {
        return port.create(context.actor(), command);
    }
}
