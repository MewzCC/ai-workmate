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

    @Test
    void submitOnlyAcceptsOneVersionedExpenseDraftAndNeverRetries() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentExpenseWriteToolDefinitions()
                .expenseSubmitDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:4a38437ed858eee879c9cadcf868a4f21e3ad0b70b17efa4dff6f1c73d5578d3");
        assertThat(definition.requiredPermissions()).containsExactly("approval:submit");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":0}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":0,\"formKey\":\"other\"}"))).isFalse();
    }

    @Test
    void withdrawOnlyAcceptsOneVersionedExpenseApplicationAndNeverRetries() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentExpenseWriteToolDefinitions()
                .expenseWithdrawToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c8e2e2a9d93a8ee8031186fb657a094d19abe0a78cb5c9dbaa0c51ceb62377e4");
        assertThat(definition.requiredPermissions()).containsExactly("approval:withdraw");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":1}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":1,\"reason\":\"skip\"}"))).isFalse();
    }

    @Test
    void reopenOnlyRestoresOneVersionedExpenseApplicationToDraft() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentExpenseWriteToolDefinitions()
                .expenseReopenToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:ccf55e1542cb739160c2bcab1ac0b8181b70d0ad0a9ef4598d0003b48e093428");
        assertThat(definition.requiredPermissions()).containsExactly("approval:reopen");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":51,\"version\":2,\"submit\":true}"))).isFalse();
    }
}
