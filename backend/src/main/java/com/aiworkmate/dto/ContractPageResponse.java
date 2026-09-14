package com.aiworkmate.dto;

import java.util.List;

public record ContractPageResponse(
        List<ContractResponse> records,
        long total,
        int page,
        int size,
        ContractStatsResponse stats,
        boolean canManage
) {
}
