package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class ApprovalRuleDefinitionQueryToolHandler
        extends TypedReadToolHandler<Long, ApprovalConfigurationToolPort.RuleDefinition> {
    private final ApprovalConfigurationToolPort port;
    public ApprovalRuleDefinitionQueryToolHandler(ApprovalConfigurationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_RULE_DEFINITION_QUERY, mapper); this.port = port;
    }
    @Override protected Long parseArguments(JsonNode arguments) {
        return BoundedToolArguments.requiredLong(arguments, "ruleId", 1);
    }
    @Override protected ApprovalConfigurationToolPort.RuleDefinition invoke(TrustedToolContext context, Long ruleId) {
        return port.readRuleDefinition(context.actor(), ruleId);
    }
}
