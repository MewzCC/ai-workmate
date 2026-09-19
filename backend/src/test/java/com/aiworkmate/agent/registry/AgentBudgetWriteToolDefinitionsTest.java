package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBudgetWriteToolDefinitionsTest {
    @Test
    void createDraftIsOneConfirmedTenantScopedWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentBudgetWriteToolDefinitions()
                .budgetCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:30d568a5b7743a291b88653c2548d4a40c39561e8647f26f155abc26a0f1cbca");
        assertThat(definition.requiredPermissions()).containsExactly("budget:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"code":"RD-2027","name":"研发预算","fiscalYear":2027,"ownerUserId":7,
                 "totalAmount":100000.00,"currency":"CNY","warningThreshold":80,"summary":"年度研发"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"code":"RD-2027","name":"研发预算","fiscalYear":2027,"ownerUserId":7,
                 "totalAmount":100000.00,"currency":"CNY","warningThreshold":80,"activate":true}
                """))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{}"))).isFalse();
    }

    @Test
    void updateDraftCannotChangeCodeStatusOrBalances() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentBudgetWriteToolDefinitions()
                .budgetUpdateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9fc377f5ffee46d9b770a4d7a588519e7b85884b2b077e18aa85b5aae4d79e5f");
        assertThat(definition.requiredPermissions()).containsExactly("budget:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"budgetId":81,"version":0,"name":"研发预算","fiscalYear":2027,
                 "ownerUserId":7,"totalAmount":120000.00,"currency":"CNY","warningThreshold":85}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"budgetId":81,"version":0,"name":"研发预算","fiscalYear":2027,
                 "ownerUserId":7,"totalAmount":120000.00,"currency":"CNY","warningThreshold":85,
                 "status":"ACTIVE"}
                """))).isFalse();
    }

    @Test
    void activateDraftIsSecondaryConfirmedAndHasNoTargetStatusArgument() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentBudgetWriteToolDefinitions()
                .budgetActivateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:51d4be688b529ca56eebc2cc04adcb6b67b0e929d15f68ea7f3a935d9c0e7611");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.requiredPermissions()).containsExactly("budget:manage");
        assertThat(validator.valid(definition.inputSchema(),
                mapper.readTree("{\"budgetId\":81,\"version\":1}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"budgetId\":81,\"version\":1,\"status\":\"ACTIVE\"}"))).isFalse();
    }
}
