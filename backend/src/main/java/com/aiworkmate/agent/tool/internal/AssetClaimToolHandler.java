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
public final class AssetClaimToolHandler
        extends TypedWriteToolHandler<AssetToolPort.ClaimCommand, AssetToolPort.ClaimResult> {
    private final AssetToolPort port;

    public AssetClaimToolHandler(AssetToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ASSET_CLAIM, objectMapper);
        this.port = port;
    }

    @Override
    protected AssetToolPort.ClaimCommand parseArguments(JsonNode arguments) {
        return new AssetToolPort.ClaimCommand(
                requiredLong(arguments, "assetId", 1),
                requiredLong(arguments, "employeeId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                optionalText(arguments, "reason"));
    }

    @Override
    protected AssetToolPort.ClaimResult invoke(
            TrustedToolContext context, AssetToolPort.ClaimCommand command) {
        return port.claim(context.actor(), command, stableOperationKey(context));
    }
}
