package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record TenantFeaturesRequest(
        @NotNull Boolean approvalEnabled,
        @NotNull Boolean attendanceEnabled,
        @NotNull Boolean assetEnabled,
        @NotNull Boolean meetingEnabled,
        @NotNull Boolean visitorEnabled,
        @NotNull Boolean sealEnabled,
        @NotNull Integer version
) {
}
