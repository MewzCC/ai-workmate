package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalRuleUpdateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.RuleDraftUpdate, ApprovalConfigurationToolPort.RuleDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalRuleUpdateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_RULE_UPDATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.RuleDraftUpdate parseArguments(JsonNode arguments) {
        return ApprovalRuleDraftArguments.update(arguments);
    }

    @Override
    protected ApprovalConfigurationToolPort.RuleDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.RuleDraftUpdate command) {
        return port.updateRuleDraft(context.actor(), command);
    }
}
