package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

abstract class AbstractAssetMaintenanceToolHandler
        extends TypedWriteToolHandler<AssetToolPort.MaintenanceCommand, AssetToolPort.LifecycleResult> {
    protected final AssetToolPort port;

    protected AbstractAssetMaintenanceToolHandler(ToolCode code, AssetToolPort port, ObjectMapper mapper) {
        super(code, mapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.MaintenanceCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.MaintenanceCommand(requiredLong(arguments, "assetId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "reason"));
    }
}
