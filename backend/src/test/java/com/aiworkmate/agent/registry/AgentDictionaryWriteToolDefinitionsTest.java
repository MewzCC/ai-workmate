package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDictionaryWriteToolDefinitionsTest {
    @Test
    void definesCreateAndUpdateWithClosedConfirmedContracts() throws Exception {
        var definitions = new AgentDictionaryWriteToolDefinitions();
        var definition = definitions.dictionaryTypeCreateToolDefinition(new ObjectMapper());

        assertThat(definition.code()).isEqualTo("dictionary.type.create");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:89f0b84f72ca1c6a026e551921ddedb7b38aa69ab2abe91cd16e66c8f7797cdc");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.requiredPermissions()).containsExactly("dictionary:manage");
        assertThat(definition.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.inputSchema().path("properties").has("userId")).isFalse();
        assertThat(definition.inputSchema().path("properties").has("tenantId")).isFalse();

        var update = definitions.dictionaryTypeUpdateToolDefinition(new ObjectMapper());
        assertThat(update.code()).isEqualTo("dictionary.type.update");
        assertThat(update.schemaHash()).isEqualTo(
                "sha256:1885b4e3284a75c88784f5318f5fbf0a7fb0c12a31699eb4cb4e7e73101d5678");
        assertThat(update.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(update.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(update.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(update.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(update.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("code", "version");
        assertThat(update.inputSchema().path("properties").has("status")).isFalse();

        var createItem = definitions.dictionaryItemCreateToolDefinition(new ObjectMapper());
        assertThat(createItem.code()).isEqualTo("dictionary.item.create");
        assertThat(createItem.schemaHash()).isEqualTo(
                "sha256:e02f5ca5efeaca0e10f3b00ea3312a2cb03d6fd88900cabb1e43781627d9f742");
        assertThat(createItem.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(createItem.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(createItem.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(createItem.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(createItem.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("typeCode", "value", "label");
        assertThat(createItem.inputSchema().path("properties").has("dictionaryTypeId")).isFalse();

        var updateItem = definitions.dictionaryItemUpdateToolDefinition(new ObjectMapper());
        assertThat(updateItem.code()).isEqualTo("dictionary.item.update");
        assertThat(updateItem.schemaHash()).isEqualTo(
                "sha256:6b132372c671d8449c5b998e46e3405b64a044474db6230867e9739c658bfc9b");
        assertThat(updateItem.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(updateItem.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(updateItem.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(updateItem.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(updateItem.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("typeCode", "value", "version");
        assertThat(updateItem.inputSchema().path("properties").has("status")).isFalse();

        var updateTypeStatus = definitions.dictionaryTypeUpdateStatusToolDefinition(new ObjectMapper());
        assertThat(updateTypeStatus.code()).isEqualTo("dictionary.type.updateStatus");
        assertThat(updateTypeStatus.schemaHash()).isEqualTo(
                "sha256:bfa7721b31746645df0cded2372f6a5a58e2a8ccd0b345d92751e806a182203d");
        assertThat(updateTypeStatus.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(updateTypeStatus.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(updateTypeStatus.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(updateTypeStatus.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(updateTypeStatus.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("code", "version", "status");

        var updateItemStatus = definitions.dictionaryItemUpdateStatusToolDefinition(new ObjectMapper());
        assertThat(updateItemStatus.code()).isEqualTo("dictionary.item.updateStatus");
        assertThat(updateItemStatus.schemaHash()).isEqualTo(
                "sha256:a427c5f87244f9808d49c39e3f35905928e36cc0933bb6c34dfacef16dc12acd");
        assertThat(updateItemStatus.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(updateItemStatus.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(updateItemStatus.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(updateItemStatus.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(updateItemStatus.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("typeCode", "value", "version", "status");
    }
}
