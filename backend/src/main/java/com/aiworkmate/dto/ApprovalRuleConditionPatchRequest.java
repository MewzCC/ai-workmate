package com.aiworkmate.dto;

/** Internal single-condition value command; conditionNumber is one-based. */
public record ApprovalRuleConditionPatchRequest(Integer version, Integer conditionNumber, String value) { }
