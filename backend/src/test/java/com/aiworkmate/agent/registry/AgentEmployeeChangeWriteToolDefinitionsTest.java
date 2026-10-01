package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEmployeeChangeWriteToolDefinitionsTest {
    @Test
    void applicationIsOneConfirmedTenantScopedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentEmployeeChangeWriteToolDefinitions()
                .employeeChangeApplyToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:958d37b7a487f4ac1f736907a017678b20fc27afa341205a5f7b7ab93ef82303");
        assertThat(definition.requiredPermissions()).containsExactly("hr:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"employeeUserId":31,"changeType":"TRANSFER","effectiveDate":"2026-09-30",
                 "targetDepartmentId":4,"targetPositionId":5,"reviewApproverUserId":12,
                 "reason":"团队调整"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"employeeUserId":31,"changeType":"TRANSFER","effectiveDate":"2026-09-30",
                 "reviewApproverUserId":12,"reason":"团队调整","applicantUserId":7}
                """))).isFalse();
    }

    @Test
    void decisionsAreAssignedL2AndWithdrawalIsOwnedL1() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definitions = new AgentEmployeeChangeWriteToolDefinitions();
        ToolSchemaValidator validator = new ToolSchemaValidator();
        ToolDefinition approve = definitions.employeeChangeApproveToolDefinition(mapper);
        ToolDefinition reject = definitions.employeeChangeRejectToolDefinition(mapper);
        ToolDefinition withdraw = definitions.employeeChangeWithdrawToolDefinition(mapper);

        assertThat(approve.schemaHash()).isEqualTo(
                "sha256:9525bd2d59e4ae2eb5195a0ffda94a19d53c4c73236a5122b5eb7424c418761b");
        assertThat(reject.schemaHash()).isEqualTo(
                "sha256:92118412e490ebdd270eceb4ba99b0a313c460710e978aa740dd2d7f968e7870");
        assertThat(withdraw.schemaHash()).isEqualTo(
                "sha256:b08cddab012490cf1ebaebc02b1a94513e73f1e29a4528b5968e9f4d27e4d247");
        assertThat(approve.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(reject.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(approve.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(approve.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(reject.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(withdraw.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(withdraw.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(withdraw.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(approve.inputSchema(), mapper.readTree(
                "{\"changeId\":41,\"expectedVersion\":0}"))).isTrue();
        assertThat(validator.valid(reject.inputSchema(), mapper.readTree(
                "{\"changeId\":41,\"expectedVersion\":0,\"comment\":\"资料不完整\"}"))).isTrue();
        assertThat(validator.valid(reject.inputSchema(), mapper.readTree(
                "{\"changeId\":41,\"expectedVersion\":0}"))).isFalse();
        assertThat(validator.valid(withdraw.inputSchema(), mapper.readTree(
                "{\"changeId\":41,\"expectedVersion\":0,\"userId\":7}"))).isFalse();
    }
}
