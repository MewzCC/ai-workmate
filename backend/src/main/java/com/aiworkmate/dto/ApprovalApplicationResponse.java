package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用审批申请单响应。
 *
 * <p>{@code timeline} 仅详情接口返回（提交/审批动作记录），
 * 列表接口为 {@code null}；{@code canWithdraw} 预留给后续通用撤回链路。
 */
public record ApprovalApplicationResponse(
        Long id,
        Long applicantUserId,
        String applicantName,
        String formKey,
        String formName,
        String title,
        String dataJson,
        String formSchemaSnapshot,
        Integer formVersionSnapshot,
        String processNodeSnapshot,
        Integer processVersionSnapshot,
        String ruleSnapshot,
        String status,
        Integer version,
        Long taskId,
        Integer taskVersion,
        String taskStatus,
        LocalDateTime taskDueAt,
        boolean overdue,
        int reminderCount,
        LocalDateTime lastRemindedAt,
        LocalDateTime remindAvailableAt,
        boolean canRemind,
        Long taskAssigneeUserId,
        String taskAssigneeName,
        String workflowStatus,
        List<WorkflowTimelineResponse> timeline,
        boolean canWithdraw,
        boolean canEditDraft,
        boolean canCancel,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
