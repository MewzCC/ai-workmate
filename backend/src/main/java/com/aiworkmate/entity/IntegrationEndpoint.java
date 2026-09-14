package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("integration_endpoint")
public class IntegrationEndpoint {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String endpointCode;
    private String name;
    private String upstreamCode;
    private String httpMethod;
    private String relativePath;
    private String requestTemplate;
    private String description;
    private String status;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
