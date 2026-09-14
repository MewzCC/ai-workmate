package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier_status_history")
public class SupplierStatusHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long supplierId;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Long operatorId;
    private String operatorLabel;
    private LocalDateTime createdAt;
}
