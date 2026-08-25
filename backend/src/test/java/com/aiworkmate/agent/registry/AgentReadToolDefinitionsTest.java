package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReadToolDefinitionsTest {

    @Test
    void todoQueryDefinitionMatchesFrozenDatabaseSeed() throws Exception {
        ToolDefinition definition = new AgentReadToolDefinitions()
                .todoQueryToolDefinition(new ObjectMapper());

        assertThat(definition.code()).isEqualTo("todo.query");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:5587d07883805ecb5810979e984dc33044c68898a9e9aeed4d07c4f3a9793c69");
        assertThat(definition.requiredPermissions()).containsExactly("todo:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.maxResultItems()).isEqualTo(50);
        assertThat(definition.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.inputSchema().path("properties").has("userId")).isFalse();
        assertThat(definition.inputSchema().path("properties").path("size").path("maximum").asInt())
                .isEqualTo(50);
    }

    @Test
    void todoQuerySchemaRejectsIdentityForgeryUnknownFieldsAndOverLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .todoQueryToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"status\":\"PENDING\",\"page\":1,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"userId\":999}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"tenantId\":999}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"size\":51}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"page\":\"1\"}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"filters\":{\"$ref\":\"file:///etc/passwd\"}}"))).isFalse();
    }
}
