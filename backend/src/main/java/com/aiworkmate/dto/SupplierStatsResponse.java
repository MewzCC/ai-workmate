package com.aiworkmate.dto;

public record SupplierStatsResponse(long total, long active, long suspended, long blacklisted) {
}
