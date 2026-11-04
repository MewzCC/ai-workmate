package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AgentAtomicApplicationToolDefinitionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentAtomicApplicationToolDefinitions definitions = new AgentAtomicApplicationToolDefinitions();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();

    @Test void requiresOneExplicitNonRetryableSelfOwnedWrite() throws Exception {
        for (var tool : List.of(definitions.approvalApplicationApply(json), definitions.expenseApply(json))) {
            assertThat(tool.requiredPermissions()).containsExactlyInAnyOrder("approval:create", "approval:submit");
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
            assertThat(tool.maxResultItems()).isEqualTo(1);
        }
    }
    @Test void expenseRequiresCompleteFieldsAndRejectsAuthorityInjection() throws Exception {
        var tool = definitions.expenseApply(json);
        var valid = json.readTree("""
                {"amount":88.50,"category":"TRAVEL","expenseDate":"2026-11-01","reason":"synthetic expense"}
                """);
        assertThat(validator.valid(tool.inputSchema(), valid)).isTrue();
        for (String field : List.of("amount", "category", "expenseDate", "reason")) {
            var incomplete = (com.fasterxml.jackson.databind.node.ObjectNode) valid.deepCopy();
            incomplete.remove(field);
            assertThat(validator.valid(tool.inputSchema(), incomplete)).as(field).isFalse();
        }
        var forged = (com.fasterxml.jackson.databind.node.ObjectNode) valid.deepCopy();
        forged.put("tenantId", 2);
        assertThat(validator.valid(tool.inputSchema(), forged)).isFalse();
        assertThat(tool.schemaHash()).isEqualTo("sha256:d5e7ac51b2bfd63720ac4e27277a3799d861f2173eddd8330270efbff9e2225e");
    }
}
