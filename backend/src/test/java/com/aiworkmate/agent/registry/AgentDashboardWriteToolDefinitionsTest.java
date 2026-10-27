package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDashboardWriteToolDefinitionsTest {
    @Test
    void metricPreferenceIsConfirmedSelfOwnedAndClosedToUnknownFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentDashboardWriteToolDefinitions()
                .dashboardPreferencesUpdateToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:bdfa0ed9774119150ea82446ad14fdebdfd5f88e21652a3bf82511301c8e9a96");
        assertThat(definition.requiredPermissions()).containsExactly("dashboard:read");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"metricCodes":["UNREAD_MESSAGES","PENDING_TODOS"]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"metricCodes":["UNREAD_MESSAGES"],"userId":99}
                """))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"metricCodes":["TENANT_REVENUE"]}
                """))).isFalse();
    }
}
