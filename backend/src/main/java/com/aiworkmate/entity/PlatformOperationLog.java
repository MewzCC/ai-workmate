package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_operation_log")
public class PlatformOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String actorLabel;
    private String eventType;
    private String httpMethod;
    private String requestPath;
    private String outcome;
    private Integer statusCode;
    private Long durationMs;
    private String clientIp;
    private String userAgent;
    private String requestId;
    private String traceId;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
