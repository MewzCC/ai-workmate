package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 审批表单定义详情。
 */
public record ApprovalFormResponse(
        Long id,
        String formKey,
        String formName,
        String description,
        String schemaJson,
        String status,
        Integer version,
        String creatorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}