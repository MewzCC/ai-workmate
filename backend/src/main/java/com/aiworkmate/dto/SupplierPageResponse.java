package com.aiworkmate.dto;

import java.util.List;

public record SupplierPageResponse(
        List<SupplierResponse> records,
        long total,
        int page,
        int size,
        SupplierStatsResponse stats,
        boolean canManage
) {
}
