package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 通用审批申请单视图（Mapper 联表结果）。
 *
 * <p>附带申请人姓名、最近一条工作流任务（含受理人）与流程实例状态，
 * 供「我的申请」与详情接口直接组装响应。
 */
public record ApprovalApplicationView(
        Long id,
        Long applicantUserId,
        String applicantName,
        Long formId,
        String formKey,
        String formName,
        String title,
        String dataJson,
        String status,
        Integer version,
        Long taskId,
        Integer taskVersion,
        String taskStatus,
        LocalDateTime taskDueAt,
        Long taskAssigneeUserId,
        String taskAssigneeName,
        String workflowStatus,
        Long workflowInstanceId,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
