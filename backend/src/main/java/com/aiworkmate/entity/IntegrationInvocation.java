package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("integration_invocation")
public class IntegrationInvocation {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long endpointId;
    private String requestHash;
    private String outcome;
    private Integer httpStatus;
    private Long durationMs;
    private String responsePreview;
    private String errorCode;
    private String traceId;
    private Long operatorId;
    private String operatorLabel;
    private LocalDateTime createdAt;
}
