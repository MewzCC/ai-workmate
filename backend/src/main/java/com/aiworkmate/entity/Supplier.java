package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String supplierCode;
    private String name;
    private String shortName;
    private String unifiedSocialCreditCode;
    private String category;
    private String supplierLevel;
    private String status;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String paymentTerms;
    private String riskNote;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
