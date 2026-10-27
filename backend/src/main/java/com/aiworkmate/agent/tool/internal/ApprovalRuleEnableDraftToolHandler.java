package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class ApprovalRuleEnableDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.VersionedRule, ApprovalConfigurationToolPort.RuleDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalRuleEnableDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_RULE_ENABLE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.VersionedRule parseArguments(JsonNode arguments) {
        return new ApprovalConfigurationToolPort.VersionedRule(
                requiredLong(arguments, "ruleId", 1),
                requiredInt(arguments, "version", 1, Integer.MAX_VALUE));
    }

    @Override
    protected ApprovalConfigurationToolPort.RuleDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.VersionedRule command) {
        return port.enableRuleDraft(context.actor(), command);
    }
}
