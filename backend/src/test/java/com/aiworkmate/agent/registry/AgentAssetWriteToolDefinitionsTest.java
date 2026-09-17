package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAssetWriteToolDefinitionsTest {
    @Test
    void claimIsOneConfirmedTenantScopedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentAssetWriteToolDefinitions().assetClaimToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:70e57baaa8ec2003241bf090427125dad1ae0da3537813e62ce88d2615a2f9b5");
        assertThat(definition.requiredPermissions()).containsExactly("asset:claim");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"employeeId\":20,\"version\":2,\"reason\":\"新员工领用\"}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"employeeId\":20,\"version\":2,\"tenantId\":99}"))).isFalse();
    }

    @Test
    void returnIsOneConfirmedTenantScopedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentAssetWriteToolDefinitions().assetReturnToolDefinition(mapper);

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:2370eb1e5d8a544c52f331ccd8deaff7865ee1db276844e2237f63496ee95f3e");
        assertThat(definition.requiredPermissions()).containsExactly("asset:return");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"version\":3,\"userId\":7}"))).isFalse();
    }
}
