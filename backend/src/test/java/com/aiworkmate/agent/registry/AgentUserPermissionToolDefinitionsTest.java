package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentUserPermissionToolDefinitionsTest {
    @Test
    void exposesOnlyBoundedCurrentActorPermissionQuery() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentUserPermissionToolDefinitions()
                .userPermissionMineQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.code()).isEqualTo("userPermission.mine.query");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.requiredPermissions()).containsExactly("user-permission:read:self");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3a7a57d10e446b40d09a32d49c9a94bd017872d9e2f823b1890802d6f1e11128");
        assertThat(definition.inputSchema().toString())
                .doesNotContain("userId", "tenantId", "role", "permission", "dataScope");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"keyword\":\"approval\",\"page\":1,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"userId\":9}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"size\":51}"))).isFalse();
    }
}
