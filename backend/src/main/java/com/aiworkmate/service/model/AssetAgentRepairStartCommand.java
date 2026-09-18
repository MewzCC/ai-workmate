package com.aiworkmate.service.model;

/** Framework-neutral command for one Agent asset repair registration. */
public record AssetAgentRepairStartCommand(long assetId, int expectedVersion, String reason) {
}
