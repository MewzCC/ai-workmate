package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntegrationEndpointWriteToolDefinitionsTest {
    @Test
    void definesDraftOnlyCreateWithClosedSecondaryConfirmationContract() throws Exception {
        var definition = new AgentIntegrationEndpointWriteToolDefinitions()
                .integrationEndpointCreateDraftToolDefinition(new ObjectMapper());

        assertThat(definition.code()).isEqualTo("integration.endpoint.createDraft");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:0bd660271acaffa2206989975ab46244c231f6c1d3794b4828707b1211fd19d9");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.requiredPermissions()).containsExactly("integration:endpoint:manage");
        assertThat(definition.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("code", "name", "upstreamCode", "method", "relativePath");
        assertThat(definition.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.inputSchema().path("properties").has("url")).isFalse();
        assertThat(definition.outputSchema().toString()).doesNotContain("requestTemplate");
    }

    @Test
    void definesOptimisticDraftOnlyUpdateWithExplicitConfirmation() throws Exception {
        var definition = new AgentIntegrationEndpointWriteToolDefinitions()
                .integrationEndpointUpdateDraftToolDefinition(new ObjectMapper());

        assertThat(definition.code()).isEqualTo("integration.endpoint.updateDraft");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:58259cb9641a8bb4eaa14c2a8e7a0927dd2887812138160bf8aa65122300470a");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.inputSchema().path("required")).extracting(JsonNode::asText)
                .containsExactly("endpointId", "version", "code", "name", "upstreamCode", "method", "relativePath");
        assertThat(definition.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.inputSchema().path("properties").has("url")).isFalse();
        assertThat(definition.outputSchema().toString()).doesNotContain("requestTemplate");
    }
}
