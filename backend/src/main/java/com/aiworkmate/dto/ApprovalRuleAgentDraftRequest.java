package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded rule-draft command used by the controlled Agent adapter. */
public record ApprovalRuleAgentDraftRequest(
        String ruleKey, String ruleName, String ruleType, Integer priority, String description,
        String logic, List<Condition> conditions, Action action
) {
    public ApprovalRuleAgentDraftRequest { conditions = List.copyOf(conditions); }

    public record Condition(String field, String operator, String value) { }
    public record Action(String appendNode, boolean enabled, String mode) { }
}
