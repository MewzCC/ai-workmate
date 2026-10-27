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
                "sha256:7286724aabd5e431fe911c9a9b3ec9f9984be9431fe9cf3dd436c4dea4a6b806",
                "sha256:e4f18dfeab7b4995534312eeef5e2aa1fbf2590fd4aa17d5299b3070aaed8b51",
                "sha256:d10931f47f7a8c029913766cb429a38e2caef5ad4c5d72e992ca8220632f6870");
    }
}
