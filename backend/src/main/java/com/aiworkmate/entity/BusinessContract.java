package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("business_contract")
public class BusinessContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String contractCode;
    private String name;
    private String contractType;
    private String counterpartyName;
    private Long supplierId;
    private String supplierLabel;
    private Long ownerUserId;
    private String ownerLabel;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String currency;
    private LocalDate signedDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String fulfillmentStatus;
    private String summary;
    private Integer reminderCount;
    private LocalDateTime lastRemindedAt;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
