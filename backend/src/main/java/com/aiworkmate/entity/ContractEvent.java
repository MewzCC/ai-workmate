package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("contract_event")
public class ContractEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long contractId;
    private String eventType;
    private String fromValue;
    private String toValue;
    private BigDecimal amount;
    private String detail;
    private Long operatorId;
    private String operatorLabel;
    private LocalDateTime createdAt;
}
