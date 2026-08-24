package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 审批规则新增/修改请求。
 *
 * @param ruleType     规则类型：AMOUNT_THRESHOLD / LEAVE_TYPE / EMPLOYEE_LEVEL / LIMIT_OVERRIDE
 * @param priority     执行优先级，数字越小越先执行
 * @param conditionJson 条件表达式 JSON 对象，如 {@code {"field":"amount","op":"gte","value":5000}}
 * @param actionJson    命中后动作 JSON 对象，如 {@code {"appendNode":"FINANCE_REVIEW","enabled":true}}
 * @param version       修改时必传，用于乐观锁冲突检测；新增时忽略
 */
public record ApprovalRuleRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$",
                message = "{validation.approval.key.invalid}")
        String ruleKey,
        @NotBlank @Size(max = 120) String ruleName,
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "AMOUNT_THRESHOLD|LEAVE_TYPE|EMPLOYEE_LEVEL|LIMIT_OVERRIDE",
                message = "{validation.approval.ruleType.invalid}")
        String ruleType,
        @Min(0) Integer priority,
        @NotBlank @Size(max = 8000) String conditionJson,
        @NotBlank @Size(max = 8000) String actionJson,
        @Size(max = 500) String description,
        @Pattern(regexp = "ENABLED|DISABLED",
                message = "{validation.approval.status.invalid}")
        String status,
        Integer version
) {
}