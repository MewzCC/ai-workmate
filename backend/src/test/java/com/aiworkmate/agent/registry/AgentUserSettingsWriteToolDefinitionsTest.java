package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentUserSettingsWriteToolDefinitionsTest {
    @Test
    void personalSettingsAreConfirmedSelfOwnedAndRejectSensitiveInjection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentUserSettingsWriteToolDefinitions()
                .userSettingsUpdateToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3feb8fb4d05d55598844578f85d61f905114ceb4ac1bca9096f0b06dfbc11e59");
        assertThat(definition.requiredPermissions()).containsExactly("settings:self:update");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"model":"deepseek-v4-pro","maxContextRounds":12,"stream":true,"forcePdfOcr":false}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"model":"deepseek-v4-pro","maxContextRounds":12,"stream":true,"forcePdfOcr":false,"userId":99,"apiKey":"secret","baseUrl":"https://evil.example"}
                """))).isFalse();
    }
}
