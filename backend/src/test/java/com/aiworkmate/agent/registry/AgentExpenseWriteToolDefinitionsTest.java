package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExpenseWriteToolDefinitionsTest {
    @Test
    void draftIsOneConfirmedSelfOwnedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentExpenseWriteToolDefinitions()
                .expenseCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9de3b32d7f7a3bc4910ca7f531886a9322d5e3e983a6c140beea948d77e22d4e");
        assertThat(definition.requiredPermissions()).containsExactly("approval:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"amount":88.50,"category":"TRAVEL","expenseDate":"2026-09-17",
                 "invoiceNumber":"INV-1","reason":"客户拜访"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"amount":88.50,"formKey":"other-form"}
                """))).isFalse();
    }
}
