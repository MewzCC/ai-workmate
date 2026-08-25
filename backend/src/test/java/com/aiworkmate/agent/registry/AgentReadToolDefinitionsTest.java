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

    @Test
    void leaveMineDefinitionAndExclusiveListDetailSchemaAreFrozen() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .leaveMineToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9b1d1ce3ec13c9c67f969c939c86eb4ab87659bee011ef2d32eded8a40bd26bf");
        assertThat(definition.requiredPermissions()).containsExactly("leave:read:self");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("{}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"page\":1}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"userId\":7}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"size\":51}"))).isFalse();
    }

    @Test
    void knowledgeSearchDefinitionCapsRetrievalAndMarksContentUntrusted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .knowledgeSearchToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3f65a4f7a015f1ca051fd5fad0776ed4aeed9228cf5b1059aa63ee84dab9d30f");
        assertThat(definition.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(definition.maxResultItems()).isEqualTo(10);
        assertThat(definition.outputSchema().at("/properties/untrustedContent/const").asBoolean()).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"topK\":10}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"topK\":11}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"url\":\"https://evil.invalid\"}"))).isFalse();
    }
}
