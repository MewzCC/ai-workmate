package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程定义（流程配置）。
 *
 * <p>{@code nodeJson} 保存审批节点数组 JSON 文本，节点结构如
 * {@code [{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]}，
 * 由应用层校验节点数组合法性。删除采用软删除。
 */
@Data
@TableName("approval_process")
public class ApprovalProcess {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String processKey;
    private String processName;
    private String description;
    private Long formId;
    private String nodeJson;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}