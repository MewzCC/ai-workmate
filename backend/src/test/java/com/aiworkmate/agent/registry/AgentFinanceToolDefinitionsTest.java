package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentFinanceToolDefinitionsTest {
    @Test
    void financeToolsUseSeparateClosedContractsAndHideSensitiveFields() throws Exception {
        var mapper = new ObjectMapper();
        var definitions = new AgentFinanceToolDefinitions();
        var validator = new ToolSchemaValidator();
        List<ToolDefinition> tools = List.of(definitions.expenseQueryToolDefinition(mapper),
                definitions.budgetQueryToolDefinition(mapper), definitions.contractQueryToolDefinition(mapper),
                definitions.supplierQueryToolDefinition(mapper));
        assertThat(tools).extracting(ToolDefinition::code).containsExactly(
                "expense.query", "budget.query", "contract.query", "supplier.query");
        assertThat(tools).extracting(ToolDefinition::schemaHash).containsExactly(
                "sha256:054bddbc37bd581b6feae1ad51fc702f1b5c48a4b93575b174eefbbe185f74c8",
                "sha256:7d700b1b7b6c3556ed14833b9d7d1a4bb81e461a305afb108f4b75e651dd2760",
                "sha256:51c12cd5d1f1b3297dfce9fe7d11e0f7d603766dfbd3aeb1fb0584f775acb02b",
                "sha256:7adf543de65a87fa28bbd4713d1c42f44e3522a90bacd00c12e178917d77ebe0");
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.maxResultItems()).isEqualTo(50);
            assertThat(tool.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        });
        assertThat(tools.get(0).outputSchema().toString()).doesNotContain("dataJson", "applicantUserId", "taskId");
        assertThat(tools.get(2).outputSchema().toString()).doesNotContain("ownerUserId", "supplierId");
        assertThat(tools.get(3).outputSchema().toString()).doesNotContain(
                "contactPhone", "contactEmail", "address", "unifiedSocialCreditCode", "riskNote");
        assertThat(validator.valid(tools.get(0).inputSchema(), mapper.readTree("{\"status\":\"PENDING\",\"size\":50}"))).isTrue();
        assertThat(validator.valid(tools.get(1).inputSchema(), mapper.readTree("{\"tenantId\":7}"))).isFalse();
        assertThat(validator.valid(tools.get(2).inputSchema(), mapper.readTree("{\"url\":\"https://evil.invalid\"}"))).isFalse();
        assertThat(validator.valid(tools.get(3).inputSchema(), mapper.readTree("{\"size\":51}"))).isFalse();
    }
}
