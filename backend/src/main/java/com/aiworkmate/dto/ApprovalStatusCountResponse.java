package com.aiworkmate.dto;

/**
 * 审批单状态统计（管理端审批列表页顶部指标）。
 *
 * @param status 请假单状态（DRAFT / PENDING / APPROVED / REJECTED / WITHDRAWN）
 * @param count  该状态下的单据数量
 */
public record ApprovalStatusCountResponse(String status, long count) {
}