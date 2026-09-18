package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable receipt row for one Agent-driven visitor lifecycle transition. */
@Data
@TableName("visitor_visit_operation")
public class VisitorVisitOperation {
    private Long id;
    private Long tenantId;
    private Long bookingId;
    private Long operatorUserId;
    private String operationType;
    private String agentOperationKey;
    private Integer sourceVersion;
    private Integer resultVersion;
    private String resultStatus;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
