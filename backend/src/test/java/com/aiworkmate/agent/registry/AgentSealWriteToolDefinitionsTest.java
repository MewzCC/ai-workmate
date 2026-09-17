package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSealWriteToolDefinitionsTest {
    @Test
    void sealApplyIsOneConfirmedSelfOwnedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentSealWriteToolDefinitions().sealApplyToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:7c88a6102e7a8048fcef44592c6ecf28c21bb21e1e4bd3f11c81a91ce4d80632");
        assertThat(definition.requiredPermissions()).containsExactly("seal:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"sealType":"OFFICIAL","documentTitle":"采购合同","usageReason":"签约","copies":2}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"sealType":"OFFICIAL","documentTitle":"采购合同","usageReason":"签约",
                 "copies":2,"applicantUserId":7}
                """))).isFalse();
    }

    @Test
    void sealUseRegistrationRequiresSecondaryConfirmationAndRejectsTrustedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentSealWriteToolDefinitions()
                .sealRegisterUseToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:36cb2a0464946f14877a70e591e8643fba0a01d40e5b0348dc337f787e60b8e0");
        assertThat(definition.requiredPermissions()).containsExactly("seal:register");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"usageId":41,"version":2,"actualCopies":2,"remark":"现场核对"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"usageId":41,"version":2,"actualCopies":2,"handlerUserId":7}
                """))).isFalse();
    }
}
