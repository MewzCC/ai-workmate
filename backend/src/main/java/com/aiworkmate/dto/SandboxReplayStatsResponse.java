package com.aiworkmate.dto;

public record SandboxReplayStatsResponse(
        long total,
        long matched,
        long changed,
        long successful,
        long failed
) {
}
