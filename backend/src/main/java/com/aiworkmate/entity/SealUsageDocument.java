package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seal_usage_document")
public class SealUsageDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long sealUsageId;
    private String displayName;
    private String storageKey;
    private String mimeType;
    private Long fileSize;
    private Long uploadedByUserId;
    private LocalDateTime createdAt;
}
