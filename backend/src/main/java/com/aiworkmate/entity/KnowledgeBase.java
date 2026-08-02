package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String name;
    private String icon;
    private String description;
    private String embeddingProvider;
    private String embeddingModel;
    private String rerankModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer denseTopK;
    private Integer sparseTopK;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
