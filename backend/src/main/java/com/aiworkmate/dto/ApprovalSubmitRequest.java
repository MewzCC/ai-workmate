package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 通用审批提交请求。
 *
 * <p>{@code formKey} 必填，定位启用中的表单定义；{@code processKey} 可选，
 * 缺省时自动选择该表单绑定的第一个启用流程；{@code formData} 为表单数据
 * 键值对，服务端按 {@code schema_json} 逐字段校验。
 */
public record ApprovalSubmitRequest(
        @NotBlank @Size(max = 64) String formKey,
        @Size(max = 64) String processKey,
        @NotNull @Size(max = 100) Map<String, Object> formData
) {
}
