package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class ApprovalRuleConditionPatchToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.RuleConditionPatch, ApprovalConfigurationToolPort.RuleConditionPatchResult> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalRuleConditionPatchToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_RULE_PATCH_DRAFT_CONDITION, mapper); this.port = port;
    }
    @Override protected ApprovalConfigurationToolPort.RuleConditionPatch parseArguments(JsonNode args) {
        JsonNode value = args.get("value");
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 120) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new ApprovalConfigurationToolPort.RuleConditionPatch(requiredLong(args, "ruleId", 1),
                requiredInt(args, "version", 1, Integer.MAX_VALUE - 1), requiredInt(args, "conditionNumber", 1, 10), value.textValue());
    }
    @Override protected ApprovalConfigurationToolPort.RuleConditionPatchResult invoke(TrustedToolContext context,
            ApprovalConfigurationToolPort.RuleConditionPatch command) {
        return port.patchRuleDraftCondition(context.actor(), command);
    }
}
