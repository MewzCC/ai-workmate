package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("business_audit_log")
public class BusinessAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long actorUserId;
    private String resourceType;
    private String resourceId;
    private String action;
    private String result;
    private String summary;
    private String traceId;
    private LocalDateTime createdAt;
}
