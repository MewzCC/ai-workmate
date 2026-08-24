package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 审批流程定义详情。
 */
public record ApprovalProcessResponse(
        Long id,
        String processKey,
        String processName,
        String description,
        Long formId,
        String formName,
        String nodeJson,
        String status,
        Integer version,
        String creatorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}