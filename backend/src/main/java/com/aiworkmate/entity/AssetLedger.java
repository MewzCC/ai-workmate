package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产台账。
 *
 * <p>记录行政资产的基础信息、归属部门、责任人与状态。状态枚举
 * {@code IN_USE / IDLE / REPAIRING / SCRAPPED} 由 Service 层维护。
 * 删除采用软删除（{@code deleted = true}）。
 */
@Data
@TableName("asset_ledger")
public class AssetLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String assetCode;
    private String name;
    private String category;
    private String specification;
    private String status;
    private Long departmentId;
    private Long ownerUserId;
    private LocalDate purchaseDate;
    private BigDecimal originalValue;
    private String remark;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
