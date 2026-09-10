package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_plan")
public class BudgetPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String budgetCode;
    private String name;
    private Integer fiscalYear;
    private Long ownerUserId;
    private String ownerLabel;
    private BigDecimal totalAmount;
    private BigDecimal occupiedAmount;
    private BigDecimal spentAmount;
    private String currency;
    private Integer warningThreshold;
    private String status;
    private String summary;
    private Integer version;
    private Boolean deleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
