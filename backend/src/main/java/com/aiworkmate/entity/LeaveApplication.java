package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("leave_application")
public class LeaveApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long applicantUserId;
    private Long approverUserId;
    private Long workflowInstanceId;
    private String leaveType;
    private LocalDate startDate;
    private String startPeriod;
    private LocalDate endDate;
    private String endPeriod;
    private Integer durationHalfDays;
    private String reason;
    private String status;
    private Integer version;
    private String agentOperationKey;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
