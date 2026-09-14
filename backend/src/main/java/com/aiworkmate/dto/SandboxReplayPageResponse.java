package com.aiworkmate.dto;

import java.util.List;

public record SandboxReplayPageResponse(
        List<SandboxReplayRecordResponse> records,
        long total,
        int page,
        int size,
        SandboxReplayStatsResponse stats,
        boolean canExecute
) {
}
