package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 更新本人通用审批草稿；version 用于防止多窗口覆盖。 */
public record ApprovalDraftUpdateRequest(
        @Size(max = 64) String processKey,
        @NotNull @Size(max = 100) Map<String, Object> formData,
        @NotNull @Min(0) Integer version
) {
}
