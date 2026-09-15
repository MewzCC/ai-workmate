package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSecurityGovernanceToolDefinitionsTest {
    @Test
    void definesThreeBoundedReadOnlyGovernanceTools() throws Exception {
        var d = new AgentSecurityGovernanceToolDefinitions();
        var m = new ObjectMapper();
        List<ToolDefinition> tools = List.of(d.accessGovernanceQueryToolDefinition(m), d.dataPermissionQueryToolDefinition(m), d.aiPermissionQueryToolDefinition(m));
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(tool.inputSchema().path("additionalProperties").asBoolean()).isFalse();
            assertThat(tool.outputSchema().toString()).doesNotContain("email", "actorUserId", "departmentIds", "userId");
        });
        assertThat(tools).extracting(ToolDefinition::schemaHash).containsExactly(
                "sha256:d622f57307336098c25b779333ff842a874adcbf1880ae687c08276d3c6bbc61",
                "sha256:858c22c5e8947762eda39892caf2fbf962282c21d94945491daf34ebde3f6c21",
                "sha256:270d6da876eea0376fddf296219842f25f2bdc594ec61023ac365006cfc7ae87");
    }
}
