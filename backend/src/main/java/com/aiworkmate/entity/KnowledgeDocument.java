package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_doc")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String filename;
    private Long fileSize;
    private String fileType;
    private Integer chunkCount;
    private String status;
    private String contentHash;
    private String errorMessage;
    private String embeddingProvider;
    private String embeddingModel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
