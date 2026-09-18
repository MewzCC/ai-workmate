package com.aiworkmate.service.model;

/** Framework-neutral command for one Agent asset return. */
public record AssetAgentReturnCommand(long assetId, int expectedVersion, String reason) {
}
