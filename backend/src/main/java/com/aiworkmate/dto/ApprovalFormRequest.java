package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 审批表单定义新增/修改请求。
 *
 * @param version 修改时必传，用于乐观锁冲突检测；新增时忽略
 */
public record ApprovalFormRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$",
                message = "{validation.approval.key.invalid}")
        String formKey,
        @NotBlank @Size(max = 120) String formName,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 20000) String schemaJson,
        @Pattern(regexp = "ENABLED|DISABLED",
                message = "{validation.approval.status.invalid}")
        String status,
        Integer version
) {
}