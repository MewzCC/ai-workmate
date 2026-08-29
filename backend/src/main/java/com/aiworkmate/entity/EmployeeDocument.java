package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("employee_document")
public class EmployeeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long employeeUserId;
    private String documentType;
    private String displayName;
    private String storageKey;
    private String mimeType;
    private Long fileSize;
    private Long uploadedByUserId;
    private Integer version;
    private LocalDateTime createdAt;
}
