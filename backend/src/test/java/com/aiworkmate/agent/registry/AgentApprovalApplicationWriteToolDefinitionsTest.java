package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentApprovalApplicationWriteToolDefinitionsTest {
    @Test
    void genericDraftUsesClosedTypedFieldsAndBusinessIdempotency() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationCreateDraftToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:0b91ec92030a3bdb99baac22dce2b1e7c3a239829740fe9ad174b24ecf340424");
        assertThat(definition.requiredPermissions()).containsExactly("approval:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"expense","fields":[{"name":"reason","value":"客户拜访"}]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"expense","fields":[],"tenantId":9}
                """))).isFalse();
    }

    @Test
    void genericDraftSubmitUsesOwnedVersionedNonRetryableWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationSubmitDraftToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:edbe7ad52b50a98e339f5e7cf83b572c26d3b71ddccb1f50aa11496fbed80426");
        assertThat(definition.requiredPermissions()).containsExactly("approval:submit");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2,\"tenantId\":9}"))).isFalse();
    }
}
