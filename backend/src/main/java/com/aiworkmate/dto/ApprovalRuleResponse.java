package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 审批规则详情。
 */
public record ApprovalRuleResponse(
        Long id,
        String ruleKey,
        String ruleName,
        String ruleType,
        Integer priority,
        String conditionJson,
        String actionJson,
        String description,
        String status,
        Integer version,
        String creatorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canEdit,
        boolean canDelete
) {
}