package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

/** Shared closed-world parser for semantic approval rule conditions and actions. */
final class ApprovalRuleDraftArguments {
    private static final Set<String> RULE_TYPES = Set.of("AMOUNT_THRESHOLD", "LEAVE_TYPE", "EMPLOYEE_LEVEL", "LIMIT_OVERRIDE");
    private static final Set<String> FIELDS = Set.of("amount", "durationDays", "department", "employeeLevel", "leaveType");
    private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "gte", "lt", "lte", "in");
    private static final Set<String> NODES = Set.of("DEPARTMENT_HEAD", "FINANCE_REVIEW", "DIRECT_MANAGER");
    private static final Set<String> MODES = Set.of("COUNTERSIGN", "OR_SIGN", "SEQUENTIAL");

    private ApprovalRuleDraftArguments() { }

    static ApprovalConfigurationToolPort.RuleDraft create(JsonNode arguments) {
        String ruleType = requiredText(arguments, "ruleType");
        String logic = requiredText(arguments, "logic");
        if (!RULE_TYPES.contains(ruleType) || !("AND".equals(logic) || "OR".equals(logic))) throw invalid();
        JsonNode values = arguments.get("conditions");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 10) throw invalid();
        List<ApprovalConfigurationToolPort.RuleCondition> conditions = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            String field = requiredText(value, "field");
            String operator = requiredText(value, "operator");
            String conditionValue = requiredText(value, "value");
            if (!FIELDS.contains(field) || !OPERATORS.contains(operator) || conditionValue.length() > 120) throw invalid();
            conditions.add(new ApprovalConfigurationToolPort.RuleCondition(field, operator, conditionValue));
        }
        JsonNode action = arguments.get("action");
        if (action == null || !action.isObject()) throw invalid();
        String appendNode = requiredText(action, "appendNode");
        String mode = requiredText(action, "mode");
        JsonNode enabled = action.get("enabled");
        if (!NODES.contains(appendNode) || !MODES.contains(mode) || enabled == null || !enabled.isBoolean()) throw invalid();
        return new ApprovalConfigurationToolPort.RuleDraft(requiredText(arguments, "ruleKey"),
                requiredText(arguments, "ruleName"), ruleType,
                requiredInt(arguments, "priority", 0, 10000),
                optionalTextPreservingEmpty(arguments, "description"), logic, List.copyOf(conditions),
                new ApprovalConfigurationToolPort.RuleAction(appendNode, enabled.booleanValue(), mode));
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.REQUEST_INVALID); }
}
