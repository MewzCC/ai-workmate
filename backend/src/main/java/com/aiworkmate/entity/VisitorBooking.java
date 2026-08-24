package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访客来访预约。
 *
 * <p>审批走通用 workflow（{@code business_type = VISITOR_BOOKING}）：
 * 提交后由 {@link com.aiworkmate.entity.WorkflowInstance} 驱动单级审批，
 * 审批通过后状态由 {@code PENDING -> APPROVED}，访客实际到访后可由前端
 * 标记为 {@code VISITED}（本版本仅维护审批侧的状态流转）。
 */
@Data
@TableName("visitor_booking")
public class VisitorBooking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long applicantUserId;
    private Long approverUserId;
    private Long workflowInstanceId;
    private String visitorName;
    private String visitorCompany;
    private String visitorPhone;
    private String purpose;
    private Long hostUserId;
    private LocalDateTime expectedVisitAt;
    private LocalDateTime expectedLeaveAt;
    private String plateNumber;
    private Integer partySize;
    private String status;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
