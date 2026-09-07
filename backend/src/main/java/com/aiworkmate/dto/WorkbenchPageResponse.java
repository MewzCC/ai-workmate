package com.aiworkmate.dto;

import java.util.List;

public record WorkbenchPageResponse(
        List<WorkbenchRecordResponse> records,
        long total,
        int page,
        int size,
        boolean canManage
) {
}
