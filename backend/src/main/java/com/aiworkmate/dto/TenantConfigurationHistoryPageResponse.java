package com.aiworkmate.dto;

import java.util.List;

public record TenantConfigurationHistoryPageResponse(
        List<TenantConfigurationHistoryResponse> records,
        long total,
        int page,
        int size
) {
}
