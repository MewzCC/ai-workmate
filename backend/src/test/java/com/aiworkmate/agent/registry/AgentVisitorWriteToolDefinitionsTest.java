package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVisitorWriteToolDefinitionsTest {
    @Test
    void visitorApplyIsOneConfirmedSelfOwnedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentVisitorWriteToolDefinitions()
                .visitorApplyToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c4bc3759bc77b4289d9391e14f018f6680234c4d1becbc0099bb207a434a877b");
        assertThat(definition.requiredPermissions()).containsExactly("visitor:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"visitorName":"访客甲","purpose":"项目交流","hostUserId":9,
                 "expectedVisitAt":"2026-09-20T09:00:00","partySize":2}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"visitorName":"访客甲","purpose":"项目交流","hostUserId":9,
                 "expectedVisitAt":"2026-09-20T09:00:00","partySize":2,"applicantUserId":7}
                """))).isFalse();
    }
}
