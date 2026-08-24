package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批规则（审批规则）。
 *
 * <p>{@code conditionJson} 为条件表达式 JSON 对象，{@code actionJson} 为命中后的动作 JSON 对象，
 * 两者合法性由应用层校验；规则引擎在审批提交阶段执行，当前版本仅维护配置。删除采用软删除。
 */
@Data
@TableName("approval_rule")
public class ApprovalRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String ruleKey;
    private String ruleName;
    private String ruleType;
    private Integer priority;
    private String conditionJson;
    private String actionJson;
    private String description;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}