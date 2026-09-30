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

@Component
public final class AssetTransferToolHandler
        extends TypedWriteToolHandler<AssetToolPort.TransferCommand, AssetToolPort.LifecycleResult> {
    private final AssetToolPort port;

    public AssetTransferToolHandler(AssetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ASSET_TRANSFER, objectMapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.TransferCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.TransferCommand(requiredLong(arguments, "assetId", 1),
                requiredLong(arguments, "targetDepartmentId", 1),
                optionalPositiveLong(arguments, "targetOwnerUserId"),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1), optionalText(arguments, "reason"));
    }

    @Override
    protected AssetToolPort.LifecycleResult invoke(
            TrustedToolContext context, AssetToolPort.TransferCommand command) {
        return port.transfer(context.actor(), command);
    }
}
