package com.aiworkmate.dto;

/** Actual changed condition only; no raw definition or unrelated rule metadata. */
public record ApprovalRuleConditionPatchResponse(long ruleId, int version, String status,
                                                 int conditionNumber, String field, String operator, String value) { }
