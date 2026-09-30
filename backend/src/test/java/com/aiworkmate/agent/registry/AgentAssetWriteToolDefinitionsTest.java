package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void repairStartRequiresAReasonAndKeepsIdentityOutsideTheSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentAssetWriteToolDefinitions().assetRepairStartToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:e890d896f8792d1e24271ab93b2cde6ce4585fdd262c20204660fddd2d95368f");
        assertThat(definition.requiredPermissions()).containsExactly("asset:repair");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"version\":4,\"reason\":\"电源故障\"}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"version\":4,\"reason\":\"\"}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"assetId\":9,\"version\":4,\"reason\":\"电源故障\",\"tenantId\":99}"))).isFalse();
    }

    @Test
    void remainingLifecycleActionsAreSingleItemVersionBoundAndNeverRetried() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definitions = new AgentAssetWriteToolDefinitions();
        var transfer = definitions.assetTransferToolDefinition(mapper);
        var repairComplete = definitions.assetRepairCompleteToolDefinition(mapper);
        var inventory = definitions.assetInventoryToolDefinition(mapper);
        var scrap = definitions.assetScrapToolDefinition(mapper);

        assertThat(transfer.schemaHash()).isEqualTo(
                "sha256:eb9cb431ae38dbd28812ecc75f18eb28d037fe559e7c7cbd5e2453ee1af081c2");
        assertThat(repairComplete.schemaHash()).isEqualTo(
                "sha256:a989b519e0620bd6e3e7bfcdf8f49c3d380595c777d7c79c74326cf0a7a22727");
        assertThat(inventory.schemaHash()).isEqualTo(
                "sha256:33bdadb187daf248f3430859902303392c3f6820a9d70ea1b52ad4362e3e798a");
        assertThat(scrap.schemaHash()).isEqualTo(repairComplete.schemaHash());
        assertThat(List.of(transfer, repairComplete, inventory, scrap)).allSatisfy(definition -> {
            assertThat(definition.requiredPermissions()).containsExactly("asset:write");
            assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        });
        assertThat(scrap.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(scrap.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
    }
}
