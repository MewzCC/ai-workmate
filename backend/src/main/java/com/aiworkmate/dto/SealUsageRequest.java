package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 印章用印申请请求。
 *
 * <p>提交即生成 PENDING 申请单并启动 workflow 单级审批，
 * 审批人取申请人直属上级 {@code approver_user_id}。
 */
public record SealUsageRequest(
        @Pattern(regexp = "OFFICIAL|CONTRACT|LEGAL|FINANCE|OTHER",
                message = "{validation.seal.type.invalid}")
        String sealType,
        @NotBlank @Size(max = 200) String documentTitle,
        @NotBlank @Size(max = 500) String usageReason,
        @Min(1) Integer copies
) {
}
