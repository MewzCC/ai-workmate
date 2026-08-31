package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 创建通用审批草稿；草稿允许必填项暂缺，但字段结构与取值类型仍由服务端校验。 */
public record ApprovalDraftRequest(
        @NotBlank @Size(max = 64) String formKey,
        @Size(max = 64) String processKey,
        @NotNull @Size(max = 100) Map<String, Object> formData
) {
}
