package com.aiworkmate.service.model;

/** Immutable result reconstructed from the append-only asset operation. */
public record AssetAgentClaimReceipt(
        long assetId,
        String status,
        int version
) { }
