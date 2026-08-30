package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssetInventoryRequest(
        @NotNull(message = "{validation.version.required}") Integer version,
        @NotBlank(message = "{validation.asset.inventory.result.required}")
        @Pattern(regexp = "MATCH|MISSING|DAMAGED|LOCATION_MISMATCH|CUSTODIAN_MISMATCH",
                message = "{validation.asset.inventory.result.invalid}") String inventoryResult,
        @Pattern(regexp = "IN_USE|IDLE|REPAIRING|SCRAPPED",
                message = "{validation.asset.status.invalid}") String actualStatus,
        Long actualDepartmentId,
        Long actualOwnerUserId,
        @Size(max = 500) String reason
) {
}
