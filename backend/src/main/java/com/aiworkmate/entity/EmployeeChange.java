package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("employee_change")
public class EmployeeChange {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long employeeUserId;
    private Long applicantUserId;
    private Long reviewApproverUserId;
    private String changeType;
    private LocalDate effectiveDate;
    private Long currentDepartmentId;
    private Long currentPositionId;
    private Long currentSupervisorUserId;
    private Long targetDepartmentId;
    private Long targetPositionId;
    private Long targetSupervisorUserId;
    private String reason;
    private String status;
    private String decisionComment;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
