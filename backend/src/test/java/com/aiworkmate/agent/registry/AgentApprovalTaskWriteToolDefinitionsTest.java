package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentApprovalTaskWriteToolDefinitionsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentApprovalTaskWriteToolDefinitions definitions = new AgentApprovalTaskWriteToolDefinitions();

    @Test
    void decisionAndParticipantActionsAreAssignedAndSecondaryConfirmed() throws Exception {
        var approve = definitions.approvalTaskApproveToolDefinition(mapper);
        var transfer = definitions.approvalTaskTransferToolDefinition(mapper);
        var addSign = definitions.approvalTaskAddSignToolDefinition(mapper);

        assertThat(approve.schemaHash()).isEqualTo(
                "sha256:a2e3f2c069b001562b3d793ef0e668cdb2a94981e75d58f93a4a69aff05072e7");
        assertThat(transfer.schemaHash()).isEqualTo(
                "sha256:232395ec5be77186482ed6cd4b0148afbce0ca21a7f5f0d0bd18a21a265bf04a");
        assertThat(addSign.schemaHash()).isEqualTo(
                "sha256:18d64a13f3cdda6bf7691f6890f0de86b7cacafeca415127720569c81bd44e70");
        assertThat(approve.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(approve.requiredPermissions()).containsExactly("approval:act");
        assertThat(approve.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(transfer.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(addSign.riskLevel()).isEqualTo(RiskLevel.L2);
    }

    @Test
    void closedSchemasRejectIdentityInjectionAndBoundAddSignMode() throws Exception {
        ToolSchemaValidator validator = new ToolSchemaValidator();
        var transfer = definitions.approvalTaskTransferToolDefinition(mapper);
        var addSign = definitions.approvalTaskAddSignToolDefinition(mapper);
        var reject = definitions.approvalTaskRejectToolDefinition(mapper);

        assertThat(validator.valid(transfer.inputSchema(), mapper.readTree(
                "{\"taskId\":18,\"targetUserId\":9,\"version\":2,\"reason\":\"轮班\"}"))).isTrue();
        assertThat(validator.valid(transfer.inputSchema(), mapper.readTree(
                "{\"taskId\":18,\"targetUserId\":9,\"version\":2,\"reason\":\"轮班\",\"tenantId\":3}"))).isFalse();
        assertThat(validator.valid(addSign.inputSchema(), mapper.readTree(
                "{\"taskId\":18,\"targetUserId\":9,\"version\":2,\"mode\":\"SIDE\",\"reason\":\"会签\"}"))).isFalse();
        assertThat(validator.valid(reject.inputSchema(), mapper.readTree(
                "{\"taskId\":18,\"version\":2,\"comment\":\"\"}"))).isFalse();
    }

    @Test
    void copyRequiresExplicitConfirmationWithoutAutomaticRetry() throws Exception {
        var copy = definitions.approvalTaskCopyToolDefinition(mapper);
        assertThat(copy.schemaHash()).isEqualTo(
                "sha256:232395ec5be77186482ed6cd4b0148afbce0ca21a7f5f0d0bd18a21a265bf04a");
        assertThat(copy.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(copy.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(copy.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
    }
}
