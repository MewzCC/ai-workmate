package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObservabilityWriteToolDefinitionsTest {
    @Test
    void chartPreferenceIsConfirmedSelfOwnedAndClosed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentObservabilityWriteToolDefinitions()
                .observabilityPreferencesUpdateToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3d38087e617264d2bf2587fa0090b63e7b7bf9ca9a260359edcf98a7dce44149");
        assertThat(definition.requiredPermissions()).containsExactly("runtime-log:read");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"charts":[{"id":"volume","kind":"volume","title":"","mode":"line",
                "content":["HUMAN"],"size":"normal","granularity":"auto"}]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"charts":[{"id":"volume","kind":"volume","title":"","mode":"line",
                "content":["HUMAN"],"size":"normal","granularity":"auto"}],"userId":9}
                """))).isFalse();
    }

    @Test
    void visualThresholdPreferenceCannotCarryIdentityOrOperationalAction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentObservabilityWriteToolDefinitions()
                .observabilityThresholdsUpdateToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:848b2842fe4a3a82da611bdf0ff6531c185eaa23bf095deac369747b79248bd8");
        assertThat(definition.requiredPermissions()).containsExactly("runtime-log:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"failedCount\":5,\"blockedCount\":null,\"p95DurationMs\":1500}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"failedCount\":5,\"sendAlert\":true}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"p95DurationMs\":600001}"))).isFalse();
    }
}
