package com.aiworkmate.service.model;

/** Minimal business receipt, without owner identities or an unbounded lifecycle history. */
public record AssetAgentMetadataReceipt(long assetId, String assetCode, String name, String status, int version) { }
