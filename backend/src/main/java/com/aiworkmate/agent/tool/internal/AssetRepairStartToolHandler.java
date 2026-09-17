package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class AssetRepairStartToolHandler
        extends TypedWriteToolHandler<AssetToolPort.RepairStartCommand, AssetToolPort.RepairStartResult> {
    private final AssetToolPort port;

    public AssetRepairStartToolHandler(AssetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ASSET_REPAIR_START, objectMapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.RepairStartCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.RepairStartCommand(
                requiredLong(arguments, "assetId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "reason"));
    }

    @Override
    protected AssetToolPort.RepairStartResult invoke(
            TrustedToolContext context, AssetToolPort.RepairStartCommand command) {
        return port.startRepair(context.actor(), command, stableOperationKey(context));
    }
}
