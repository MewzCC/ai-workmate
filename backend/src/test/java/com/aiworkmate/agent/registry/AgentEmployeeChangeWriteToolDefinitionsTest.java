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
}
