package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentNotificationWriteToolDefinitionsTest {
    @Test
    void markReadDefinitionIsConfirmedIdempotentAndIdentityFree() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentNotificationWriteToolDefinitions()
                .notificationMarkReadToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:b46cdf75d92a2dbf816572096b8b1eb184ce2040ed702150a6e371d08ba1b487");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(),
                mapper.readTree("{\"notificationId\":9}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                mapper.readTree("{\"notificationId\":9,\"userId\":7}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                mapper.readTree("{\"notificationId\":0}"))).isFalse();
    }
}
