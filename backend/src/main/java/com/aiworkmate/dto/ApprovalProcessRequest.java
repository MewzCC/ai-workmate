package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 审批流程定义新增/修改请求。
 *
 * @param formId  关联的表单定义 ID，可为空（流程暂不绑定表单）
 * @param nodeJson 审批节点数组 JSON 文本，如
 *                 {@code [{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]}
 * @param version 修改时必传，用于乐观锁冲突检测；新增时忽略
 */
public record ApprovalProcessRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$",
                message = "{validation.approval.key.invalid}")
        String processKey,
        @NotBlank @Size(max = 120) String processName,
        @Size(max = 500) String description,
        Long formId,
        @NotBlank @Size(max = 20000) String nodeJson,
        @Pattern(regexp = "ENABLED|DISABLED",
                message = "{validation.approval.status.invalid}")
        String status,
        Integer version
) {
}