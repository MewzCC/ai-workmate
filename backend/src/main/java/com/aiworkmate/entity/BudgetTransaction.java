package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_transaction")
public class BudgetTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long budgetId;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal occupiedBefore;
    private BigDecimal occupiedAfter;
    private BigDecimal spentBefore;
    private BigDecimal spentAfter;
    private String referenceCode;
    private String note;
    private Long operatorId;
    private String operatorLabel;
    private LocalDateTime createdAt;
}
