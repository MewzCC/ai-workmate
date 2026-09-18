package com.aiworkmate.service.model;

/** Immutable receipt used to verify an Agent asset return after an uncertain transport result. */
public record AssetAgentReturnReceipt(long assetId, String status, int version) {
}
