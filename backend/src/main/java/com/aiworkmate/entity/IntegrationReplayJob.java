package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("integration_replay_job")
public class IntegrationReplayJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sourceInvocationId;
    private Long endpointId;
    private String endpointCode;
    private String endpointName;
    private String httpMethod;
    private String relativePath;
    private String baselineRequestHash;
    private String baselineOutcome;
    private Integer baselineHttpStatus;
    private String baselineResponseHash;
    private String baselineResponsePreview;
    private String status;
    private Integer replayHttpStatus;
    private Long replayDurationMs;
    private String replayResponseHash;
    private String replayResponsePreview;
    private String replayErrorCode;
    private String comparisonResult;
    private String traceId;
    private String reason;
    private String idempotencyKey;
    private Long requestedBy;
    private String requestedByLabel;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
