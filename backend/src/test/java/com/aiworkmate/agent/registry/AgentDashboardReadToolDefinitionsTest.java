package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDashboardReadToolDefinitionsTest {
    @Test
    void freezesPersonalReadOnlyContractAndRejectsIdentityAndInvalidRanges() throws Exception {
        var mapper = new ObjectMapper();
        var definition = new AgentDashboardReadToolDefinitions().dashboardOverviewQueryToolDefinition(mapper);
        var validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:e27a8ab9413f2ef431e93ede1a0a0055dc3c683b3cc71f648c7c8bd383135282");
        assertThat(definition.requiredPermissions()).containsExactly("dashboard:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.NONE);
        for (String json : new String[]{"{}", "{\"days\":1}", "{\"days\":30}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(json))).isTrue();
        }
        for (String json : new String[]{"{\"days\":0}", "{\"days\":31}", "{\"days\":\"7\"}",
                "{\"tenantId\":99}", "{\"userId\":99}", "{\"days\":null}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
}
