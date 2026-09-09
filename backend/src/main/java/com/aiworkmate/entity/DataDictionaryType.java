package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_dictionary_type")
public class DataDictionaryType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String description;
    private String status;
    private Integer sortOrder;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
