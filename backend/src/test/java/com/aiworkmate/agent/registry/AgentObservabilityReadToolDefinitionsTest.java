package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentObservabilityReadToolDefinitionsTest {
    @Test void onlyFixedRangesAndLiveBusinessPermissionCanReadTenantAggregates() throws Exception {
        var mapper = new ObjectMapper();
        var definition = new AgentObservabilityReadToolDefinitions().observabilityOverviewQueryToolDefinition(mapper);
        var validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:e2b4272f1feb67c26f738a8ed15c84cf52eee23f58c2df2cfa87953d484f171b");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.requiredPermissions()).containsExactly("runtime-log:read");
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.NONE);
        for (String json : new String[]{"{}", "{\"range\":\"24h\"}", "{\"range\":\"7d\"}", "{\"range\":\"30d\"}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(json))).isTrue();
        }
        for (String json : new String[]{"{\"range\":\"365d\"}", "{\"range\":7}", "{\"range\":null}",
                "{\"tenantId\":99}", "{\"userId\":99}", "{\"from\":\"2020-01-01\"}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
}
