package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class AssetScrapToolHandler extends AbstractAssetMaintenanceToolHandler {
    public AssetScrapToolHandler(AssetToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_SCRAP, port, mapper);
    }

    @Override
    protected AssetToolPort.LifecycleResult invoke(
            TrustedToolContext context, AssetToolPort.MaintenanceCommand command) {
        return port.scrap(context.actor(), command);
    }
}
