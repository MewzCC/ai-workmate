package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class AssetInventoryToolHandler
        extends TypedWriteToolHandler<AssetToolPort.InventoryCommand, AssetToolPort.LifecycleResult> {
    private final AssetToolPort port;

    public AssetInventoryToolHandler(AssetToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_INVENTORY, mapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.InventoryCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.InventoryCommand(requiredLong(arguments, "assetId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                requiredText(arguments, "inventoryResult"), optionalText(arguments, "actualStatus"),
                optionalPositiveLong(arguments, "actualDepartmentId"),
                optionalPositiveLong(arguments, "actualOwnerUserId"), optionalText(arguments, "reason"));
    }

    @Override
    protected AssetToolPort.LifecycleResult invoke(
            TrustedToolContext context, AssetToolPort.InventoryCommand command) {
        return port.inventory(context.actor(), command);
    }
}
