package com.aiworkmate.service.model;

/** Closed domain command for one Agent-mediated asset assignment. */
public record AssetAgentClaimCommand(
        long assetId,
        long employeeId,
        int expectedVersion,
        String reason
) { }
