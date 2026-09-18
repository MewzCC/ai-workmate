package com.aiworkmate.service.model;

/** Immutable receipt used to verify an Agent repair registration. */
public record AssetAgentRepairStartReceipt(long assetId, String status, int version) {
}
