package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ApprovalRuleDefinitionToolDefinitionTest {
    @Test void exposesOnlyAnAuthorizedFixedRuleAndAClosedBoundedReadContract() throws Exception {
        assertThat(ToolCode.codes()).contains("approval.rule.definition.query");
        var mapper = new ObjectMapper(); var validator = new ToolSchemaValidator();
        var definition = new AgentReadToolDefinitions().approvalRuleDefinitionQueryToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo("sha256:a595eafd0035099b68fcf7f97867ea848d13cb214cd2bceda203936fd2031b23");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.READ_ONLY_SAFE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.NONE);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(definition.requiredPermissions()).containsExactly("approval:read");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{\"ruleId\":11}"))).isTrue();
        for (String input : List.of("{}", "{\"ruleId\":0}", "{\"ruleId\":\"11\"}", "{\"ruleId\":11,\"tenantId\":99}")) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(input))).as(input).isFalse();
        }
        var pages = new PageCapabilityCatalog();
        assertThat(pages.allowsTool("approval-rules", definition.code(), List.of("route:approval-rules"))).isTrue();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace", "route:approval-rules"))).isTrue();
        var output = mapper.readTree("{\"ruleId\":11,\"ruleKey\":\"fixture\",\"ruleName\":\"Fixture\",\"ruleType\":\"AMOUNT_THRESHOLD\",\"priority\":20,\"status\":\"DISABLED\",\"version\":3,\"logic\":\"AND\",\"conditions\":[{\"field\":\"amount\",\"operator\":\"gte\",\"value\":\"100\"}],\"action\":{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":false,\"mode\":\"OR_SIGN\"}}");
        assertThat(validator.valid(definition.outputSchema(), output)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).put("priority", Integer.MAX_VALUE);
        assertThat(validator.valid(definition.outputSchema(), output)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).put("conditionJson", "private");
        assertThat(validator.valid(definition.outputSchema(), output)).isFalse();
    }
}
