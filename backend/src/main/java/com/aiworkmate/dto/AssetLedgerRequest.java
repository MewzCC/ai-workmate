package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产台账新增/修改请求。
 */
public record AssetLedgerRequest(
        @NotBlank @Size(max = 64) String assetCode,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String category,
        @Size(max = 120) String specification,
        @Pattern(regexp = "IN_USE|IDLE|REPAIRING|SCRAPPED",
                message = "{validation.asset.status.invalid}")
        String status,
        Long departmentId,
        Long ownerUserId,
        LocalDate purchaseDate,
        BigDecimal originalValue,
        @Size(max = 500) String remark,
        Integer version
) {
}
