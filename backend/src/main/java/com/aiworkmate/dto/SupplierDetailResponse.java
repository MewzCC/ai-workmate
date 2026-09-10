package com.aiworkmate.dto;

import java.util.List;

public record SupplierDetailResponse(
        SupplierResponse supplier,
        List<SupplierStatusHistoryResponse> statusHistory
) {
}
