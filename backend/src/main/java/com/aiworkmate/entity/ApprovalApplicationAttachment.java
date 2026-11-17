package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_application_attachment")
public class ApprovalApplicationAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long applicationId;
    private Long ownerUserId;
    private String fieldName;
    private String fieldType;
    private String displayName;
    private String storageKey;
    private String mimeType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
