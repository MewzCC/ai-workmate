package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentApprovalApplicationReadToolDefinitionsTest {
    @Test void freezesSelfOnlyBoundedContract() throws Exception {
        var mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationReadToolDefinitions().approvalApplicationMineQueryToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo("sha256:f0f343b67ee165583e7d62914ac9037fa659fe2dd470b1fb7c7c27fb2328af74");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.requiredPermissions()).containsExactly("approval:mine:read");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        var validator = new ToolSchemaValidator();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{}"))).isTrue();
        for (String json : new String[]{"{\"userId\":1}", "{\"tenantId\":1}", "{\"status\":\"ALL\"}",
                "{\"size\":51}", "{\"page\":10001}", "{\"formKey\":null}", "{\"formKey\":\"\"}"}) {
            assertThat(validator.valid(definition.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
}
