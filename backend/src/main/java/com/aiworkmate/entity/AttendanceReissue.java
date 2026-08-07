package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请。
 *
 * <p>独立审批流（不接入 workflow 体系）：由申请人直属上级 {@code approver_user_id} 审批，
 * 状态流转 {@code PENDING -> APPROVED/REJECTED}。审批通过后由 Service 层自动补写
 * {@link AttendanceRecord} 对应字段并重新计算状态。
 */
@Data
@TableName("attendance_reissue")
public class AttendanceReissue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long applicantUserId;
    private Long approverUserId;
    private LocalDate clockDate;
    private String clockType;
    private String reason;
    private String status;
    private String approverComment;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
