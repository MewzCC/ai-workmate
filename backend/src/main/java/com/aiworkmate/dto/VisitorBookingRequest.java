package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 访客预约申请请求。
 *
 * <p>提交即生成 PENDING 申请单并启动 workflow 单级审批，
 * 审批人取申请人直属上级 {@code approver_user_id}。
 */
public record VisitorBookingRequest(
        @NotBlank @Size(max = 60) String visitorName,
        @Size(max = 120) String visitorCompany,
        @Size(max = 40) String visitorPhone,
        @NotBlank @Size(max = 200) String purpose,
        @NotNull Long hostUserId,
        @NotNull LocalDateTime expectedVisitAt,
        LocalDateTime expectedLeaveAt,
        @Size(max = 40) String plateNumber,
        @Min(1) Integer partySize
) {
}
