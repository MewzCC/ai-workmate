package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOperationalGovernanceToolDefinitionsTest {
    @Test
    void definesFourBoundedReadOnlyToolsWithoutSensitiveFields() throws Exception {
        var d = new AgentOperationalGovernanceToolDefinitions();
        var m = new ObjectMapper();
        List<ToolDefinition> tools = List.of(d.auditQueryToolDefinition(m),
                d.tenantConfigurationQueryToolDefinition(m), d.dictionaryQueryToolDefinition(m),
                d.systemCapabilityQueryToolDefinition(m));
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(tool.outputSchema().toString()).doesNotContain(
                    "actorUserId", "resourceId", "traceId", "summary", "endpoint", "connectionString");
        });
        assertThat(tools).extracting(ToolDefinition::schemaHash).containsExactly(
                "sha256:c20374b208d69def5365f321daa3c9d80c8c7a54ed7ab0a1b0cd10ade022ab04",
                "sha256:884afa367781930d66fdafc2b64205710c17b546099610be43be6e960531cba6",
                "sha256:d4cc084b963a3e48f1a63c8ab6b988c1f4a8ce361db6b4a74d28867cb821ff92",
                "sha256:4e80069fa10fe0a961e030bd54c85a87f69238ed9ddab94675085576bcefae88");
    }
}
