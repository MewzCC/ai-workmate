package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class AssetRepairCompleteToolHandler extends AbstractAssetMaintenanceToolHandler {
    public AssetRepairCompleteToolHandler(AssetToolPort port, ObjectMapper mapper) {
        super(ToolCode.ASSET_REPAIR_COMPLETE, port, mapper);
    }

    @Override
    protected AssetToolPort.LifecycleResult invoke(
            TrustedToolContext context, AssetToolPort.MaintenanceCommand command) {
        return port.completeRepair(context.actor(), command);
    }
}
