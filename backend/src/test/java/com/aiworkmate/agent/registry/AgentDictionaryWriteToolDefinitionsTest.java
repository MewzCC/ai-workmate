package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDictionaryWriteToolDefinitionsTest {
    @Test
    void definesOneSecondaryConfirmedTenantWrite() throws Exception {
        var definition = new AgentDictionaryWriteToolDefinitions()
                .dictionaryTypeCreateToolDefinition(new ObjectMapper());

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
    }
}
