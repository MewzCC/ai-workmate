package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskCenterToolDefinitionsTest {
    @Test
    void exposesOnlyPersonalTaskSummaries() throws Exception {
        var tool = new AgentTaskCenterToolDefinitions().agentTaskMineQueryToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(tool.requiredPermissions()).containsExactly("agent:task:read");
        assertThat(tool.outputSchema().toString()).doesNotContain(
                "\"plan\":", "\"args\":", "\"result\":", "\"input\":", "\"pageContext\":");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:1d4697a6ea535d206068ea1b0e6d8605c2c8f7d80686f90602131dde3df87658");
    }

    @Test
    void exposesConfirmedNonRetryablePersonalCancellation() throws Exception {
        var tool = new AgentTaskCenterToolDefinitions().agentTaskCancelToolDefinition(new ObjectMapper());
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.requiredPermissions()).containsExactly("agent:task:read");
        assertThat(tool.schemaHash()).isEqualTo(
                "sha256:69904eabce7fcee590879e4aef10104ae960cc6141e32b175145f4e95c325ff2");
    }
}
