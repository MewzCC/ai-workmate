package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 印章使用申请。
 *
 * <p>审批走通用 workflow（{@code business_type = SEAL_USAGE}）：
 * 提交后由 {@link com.aiworkmate.entity.WorkflowInstance} 驱动单级审批。
 * 印章类型枚举 {@code OFFICIAL / CONTRACT / LEGAL / FINANCE / OTHER}。
 */
@Data
@TableName("seal_usage")
public class SealUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long applicantUserId;
    private Long approverUserId;
    private Long workflowInstanceId;
    private String sealType;
    private String documentTitle;
    private String usageReason;
    private Integer copies;
    private String status;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
