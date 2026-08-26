package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWriteToolDefinitionsTest {

    @Test
    void leaveCreateDraftDefinitionIsConfirmedIdempotentAndClosed() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentWriteToolDefinitions()
                .leaveCreateDraftToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:cf8365a51babc334f5a03a90739a97f18b105dd034ab8ae6fb942934795f6dfa");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-01","startPeriod":"AM",
                 "endDate":"2026-09-01","endPeriod":"PM","reason":"家庭事务"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("""
                {"leaveType":"PERSONAL","startDate":"2026-09-01","startPeriod":"AM",
                 "endDate":"2026-09-01","endPeriod":"PM","reason":"家庭事务","userId":99}
                """))).isFalse();
    }

    @Test
    void leaveSubmitDefinitionIsL2NeverRetryAndVersionBound() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentWriteToolDefinitions()
                .leaveSubmitToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:b35875584e04f55f8da0b5448d32c5e89e11c32df78e6bdc2d2c2ccd11f0dce3");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"version\":0}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"version\":0,\"tenantId\":9}"))).isFalse();
    }
}
