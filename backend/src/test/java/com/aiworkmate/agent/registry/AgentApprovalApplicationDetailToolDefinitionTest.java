package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentApprovalApplicationDetailToolDefinitionTest {
    @Test void freezesExplicitSelfOnlyDetailContract() throws Exception {
        var mapper = new ObjectMapper();
        var tool = new AgentApprovalApplicationDetailToolDefinition().approvalApplicationDetailQueryToolDefinition(mapper);
        assertThat(tool.schemaHash()).isEqualTo("sha256:14cfdac4a34a1640dec04ba5469f0ffb4fdab6dfbb1b2f837cc517631c8641cf");
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(tool.requiredPermissions()).containsExactly("approval:mine:read");
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
        var validator = new ToolSchemaValidator();
        assertThat(validator.valid(tool.inputSchema(), mapper.readTree("{\"applicationId\":17}"))).isTrue();
        for (String json : new String[]{"{}", "{\"applicationId\":0}", "{\"applicationId\":\"1\"}",
                "{\"applicationId\":1,\"tenantId\":99}", "{\"applicationId\":1,\"includeSecrets\":true}"}) {
            assertThat(validator.valid(tool.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
}
