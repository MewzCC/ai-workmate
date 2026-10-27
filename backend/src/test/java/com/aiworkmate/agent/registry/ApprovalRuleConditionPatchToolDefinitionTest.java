package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ApprovalRuleConditionPatchToolDefinitionTest {
    @Test void permitsOnlyOneBoundedValueAndRequiresTheBusinessPageAndExplicitNonRetryableWrite() throws Exception {
        var mapper = new ObjectMapper(); var validator = new ToolSchemaValidator();
        var definition = new AgentApprovalRuleConditionPatchToolDefinitions().approvalRuleConditionPatchToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo("sha256:588a77eb3294d8955af4c31a203a16303a7c37f8ab54165375ef9403bf994b24");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        var valid = mapper.readTree("{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":\"ANNUAL\"}");
        assertThat(validator.valid(definition.inputSchema(), valid)).isTrue();
        for (String property : List.of("ruleId", "version", "conditionNumber", "value")) {
            var missing = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove(property);
            assertThat(validator.valid(definition.inputSchema(), missing)).as(property).isFalse();
        }
        for (String extra : List.of("tenantId", "userId", "role", "permission", "dataScope", "field", "operator", "actionJson", "conditionJson", "status", "url")) {
            var injected = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) injected).put(extra, "unexpected");
            assertThat(validator.valid(definition.inputSchema(), injected)).as(extra).isFalse();
        }
        for (String invalid : List.of("{\"ruleId\":11,\"version\":2147483647,\"conditionNumber\":2,\"value\":\"1\"}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":0,\"value\":\"1\"}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":11,\"value\":\"1\"}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":1}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":null}")) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(invalid))).isFalse();
        }
        var pages = new PageCapabilityCatalog();
        assertThat(pages.allowsTool("approval-rules", definition.code(), List.of("route:approval-rules"))).isTrue();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:approval-rules"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", definition.code(), List.of("route:ai-workspace", "route:approval-rules"))).isTrue();
        assertThat(pages.allowsTool("form-engine", definition.code(), List.of("route:form-engine", "route:approval-rules"))).isFalse();
        var receipt = mapper.readTree("{\"ruleId\":11,\"version\":4,\"status\":\"DISABLED\",\"conditionNumber\":2,\"field\":\"leaveType\",\"operator\":\"eq\",\"value\":\"ANNUAL\"}");
        assertThat(validator.valid(definition.outputSchema(), receipt)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt).put("conditionJson", "private");
        assertThat(validator.valid(definition.outputSchema(), receipt)).isFalse();
    }
}
