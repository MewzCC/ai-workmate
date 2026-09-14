package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_dictionary_item_usage")
public class DataDictionaryItemUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dictionaryItemId;
    private String resourceType;
    private String resourceId;
    private LocalDateTime createdAt;
}
