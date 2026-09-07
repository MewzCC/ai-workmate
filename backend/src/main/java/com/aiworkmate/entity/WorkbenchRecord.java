package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("workbench_record")
public class WorkbenchRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String moduleKey;
    private String recordCode;
    private String title;
    private String category;
    private String status;
    private BigDecimal amount;
    private String owner;
    private String details;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
