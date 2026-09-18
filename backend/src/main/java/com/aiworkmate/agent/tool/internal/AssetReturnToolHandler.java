package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class AssetReturnToolHandler
        extends TypedWriteToolHandler<AssetToolPort.ReturnCommand, AssetToolPort.ReturnResult> {
    private final AssetToolPort port;

    public AssetReturnToolHandler(AssetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ASSET_RETURN, objectMapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.ReturnCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.ReturnCommand(
                requiredLong(arguments, "assetId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "reason"));
    }

    @Override
    protected AssetToolPort.ReturnResult invoke(
            TrustedToolContext context, AssetToolPort.ReturnCommand command) {
        return port.returnAsset(context.actor(), command, stableOperationKey(context));
    }
}
