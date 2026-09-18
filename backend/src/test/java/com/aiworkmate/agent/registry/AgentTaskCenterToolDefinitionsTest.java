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
}
