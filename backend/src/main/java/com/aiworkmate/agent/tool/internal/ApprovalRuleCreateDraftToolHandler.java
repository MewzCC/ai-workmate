package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalRuleCreateDraftToolHandler extends TypedWriteToolHandler<
        ApprovalConfigurationToolPort.RuleDraft, ApprovalConfigurationToolPort.RuleDraftResult> {
    private final ApprovalConfigurationToolPort port;

    public ApprovalRuleCreateDraftToolHandler(ApprovalConfigurationToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_RULE_CREATE_DRAFT, objectMapper);
        this.port = port;
    }

    @Override
    protected ApprovalConfigurationToolPort.RuleDraft parseArguments(JsonNode arguments) {
        return ApprovalRuleDraftArguments.create(arguments);
    }

    @Override
    protected ApprovalConfigurationToolPort.RuleDraftResult invoke(
            TrustedToolContext context, ApprovalConfigurationToolPort.RuleDraft command) {
        return port.createRuleDraft(context.actor(), command);
    }
}
