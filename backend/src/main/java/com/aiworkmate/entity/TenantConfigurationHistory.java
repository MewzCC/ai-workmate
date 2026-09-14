package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tenant_configuration_history")
public class TenantConfigurationHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String category;
    private Integer version;
    private String tenantName;
    private String tenantShortName;
    private String locale;
    private String timezone;
    private Integer fiscalYearStartMonth;
    private Boolean approvalEnabled;
    private Boolean attendanceEnabled;
    private Boolean assetEnabled;
    private Boolean meetingEnabled;
    private Boolean visitorEnabled;
    private Boolean sealEnabled;
    private Integer defaultApprovalDays;
    private String expenseCurrency;
    private Integer passwordMinLength;
    private Integer sessionTimeoutMinutes;
    private Long changedBy;
    private LocalDateTime createdAt;
}
