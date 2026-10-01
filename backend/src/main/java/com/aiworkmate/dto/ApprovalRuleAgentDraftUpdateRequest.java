package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded rule-draft update used by the controlled Agent adapter. */
public record ApprovalRuleAgentDraftUpdateRequest(
        Integer version, String ruleName, String ruleType, Integer priority,
        String description, String logic, List<Condition> conditions, Action action
) {
    public ApprovalRuleAgentDraftUpdateRequest { conditions = List.copyOf(conditions); }

    public record Condition(String field, String operator, String value) { }

    public record Action(String appendNode, boolean enabled, String mode) { }
}
