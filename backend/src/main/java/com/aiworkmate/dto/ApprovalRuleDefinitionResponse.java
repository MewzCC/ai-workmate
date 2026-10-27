package com.aiworkmate.dto;

import java.util.List;

/** Semantic rule data only, never raw JSON, unrelated metadata or executable extensions. */
public record ApprovalRuleDefinitionResponse(long ruleId, String ruleKey, String ruleName, String ruleType,
        int priority, String status, int version, String logic, List<Condition> conditions, Action action) {
    public ApprovalRuleDefinitionResponse { conditions = List.copyOf(conditions); }
    public record Condition(String field, String operator, String value) { }
    public record Action(String appendNode, boolean enabled, String mode) { }
}
