package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentLeaveWithdrawalToolDefinitionsTest {
    @Test
    void freezesSingleOwnedConfirmedNonRetryableContract() throws Exception {
        var mapper = new ObjectMapper();
        var definition = new AgentLeaveWithdrawalToolDefinitions().leaveWithdrawalToolDefinition(mapper);
        var validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:8d093edc7124a4cd99ba92352223c2934524fe4a022d63916dc33513844571a9");
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.requiredPermissions()).containsExactly("leave:withdraw");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":0}"))).isTrue();
        for (String invalid : new String[] {
                "{\"applicationId\":10,\"version\":0,\"tenantId\":2}",
                "{\"applicationId\":10,\"version\":-1}",
                "{\"applicationId\":10,\"version\":2147483647}",
                "{\"applicationId\":10}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(invalid))).isFalse();
        }
    }
}
