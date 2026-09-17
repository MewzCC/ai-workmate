package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable receipt row for one Agent-driven seal lifecycle transition. */
@Data
@TableName("seal_usage_operation")
public class SealUsageOperation {
    private Long id;
    private Long tenantId;
    private Long usageId;
    private Long operatorUserId;
    private String operationType;
    private String agentOperationKey;
    private Integer sourceVersion;
    private Integer resultVersion;
    private String resultStatus;
    private Integer actualCopies;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
