package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOperationalGovernanceToolSecurityCorpusTest {
    @Test
    void rejectsIdentityInfrastructureAndExecutableArguments() throws Exception {
        var d = new AgentOperationalGovernanceToolDefinitions();
        var m = new ObjectMapper();
        var tools = List.of(d.auditQueryToolDefinition(m), d.tenantConfigurationQueryToolDefinition(m),
                d.dictionaryQueryToolDefinition(m), d.systemCapabilityQueryToolDefinition(m));
        for (var tool : tools) {
            for (String forbidden : List.of("userId", "tenantId", "permissions", "sql", "url", "path")) {
                var schema = tool.inputSchema().deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                        .set(forbidden, m.createObjectNode().put("type", "string"));
                assertThatThrownBy(() -> ToolDefinition.create(tool.code(), tool.name(), tool.description(),
                        tool.purpose(), tool.handlerVersion(), schema, tool.outputSchema(), tool.riskLevel(),
                        tool.requiredPermissions(), tool.permissionMode(), tool.ownershipPolicy(), tool.retryPolicy(),
                        tool.sideEffect(), tool.confirmationPolicy(), tool.maxResultItems(), tool.maxResultBytes(),
                        tool.timeoutMs(), tool.auditPolicy())).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
