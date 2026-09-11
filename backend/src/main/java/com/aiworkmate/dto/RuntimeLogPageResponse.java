package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeLogPageResponse(
        List<RuntimeLogRecordResponse> records,
        long total,
        int page,
        int size,
        LocalDateTime from,
        LocalDateTime to,
        RuntimeLogStatsResponse stats
) {
}
