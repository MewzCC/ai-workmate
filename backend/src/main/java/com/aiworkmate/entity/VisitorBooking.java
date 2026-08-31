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
 * 审批通过后按 {@code APPROVED -> CHECKED_IN -> VISITED -> LEFT}
 * 登记实际来访进度；超过预计到访时间仍未签到时可标记为 {@code NO_SHOW}。
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
    private Long registeredByUserId;
    private LocalDateTime checkedInAt;
    private LocalDateTime visitedAt;
    private LocalDateTime leftAt;
    private LocalDateTime noShowAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
